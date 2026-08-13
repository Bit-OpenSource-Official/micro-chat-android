#define _POSIX_C_SOURCE 200112L

#include <jni.h>
#include <ctype.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

#include "mbedtls/ctr_drbg.h"
#include "mbedtls/error.h"
#include "mbedtls/net_sockets.h"
#include "mbedtls/ssl.h"
#include "mbedtls/x509_crt.h"

#define OTA_TLS_VERSION 1
#define MAX_URL 4096
#define MAX_HOST 256
#define MAX_PATH 4096
#define MAX_LINE 16384
#define MAX_REDIRECTS 5
#define IO_BUFFER 16384

#ifdef OTA_TLS_TEST_MAIN
#define TRACE(message) do { fprintf(stderr, "%s\n", message); fflush(stderr); } while (0)
#else
#define TRACE(message) do { } while (0)
#endif

typedef struct {
    char host[MAX_HOST];
    char path[MAX_PATH];
} parsed_url;

typedef struct {
    mbedtls_net_context net;
    mbedtls_ssl_context ssl;
    mbedtls_ssl_config config;
    mbedtls_x509_crt roots;
    mbedtls_ctr_drbg_context rng;
} tls_connection;

typedef struct {
    mbedtls_ssl_context *ssl;
    unsigned char buffer[IO_BUFFER];
    size_t position;
    size_t length;
} reader;

typedef struct {
    unsigned char *data;
    size_t length;
    size_t capacity;
    size_t maximum;
    FILE *file;
} sink;

typedef struct {
    int status;
    int chunked;
    int has_length;
    uint64_t content_length;
    char location[MAX_URL];
} response_headers;

static void set_error(char *error, size_t size, const char *message) {
    snprintf(error, size, "%s", message);
}

static void set_mbed_error(char *error, size_t size, const char *operation, int code) {
    char detail[160];
    mbedtls_strerror(code, detail, sizeof(detail));
    snprintf(error, size, "%s: %s (-0x%04x)", operation, detail, (unsigned int)(-code));
}

static int system_random(void *context, unsigned char *output, size_t length) {
    (void)context;
    size_t written = 0;
    int fd = open("/dev/urandom", O_RDONLY);
    if (fd < 0) return MBEDTLS_ERR_ENTROPY_SOURCE_FAILED;
    while (written < length) {
        ssize_t result = read(fd, output + written, length - written);
        if (result > 0) {
            written += (size_t)result;
        } else if (result < 0 && errno == EINTR) {
            continue;
        } else {
            close(fd);
            return MBEDTLS_ERR_ENTROPY_SOURCE_FAILED;
        }
    }
    close(fd);
    return 0;
}

static int allowed_host(const char *host) {
    static const char *suffixes[] = {".github.com", ".githubusercontent.com"};
    size_t host_length = strlen(host);
    if (strcasecmp(host, "github.com") == 0) return 1;
    for (size_t i = 0; i < sizeof(suffixes) / sizeof(suffixes[0]); ++i) {
        size_t suffix_length = strlen(suffixes[i]);
        if (host_length > suffix_length &&
            strcasecmp(host + host_length - suffix_length, suffixes[i]) == 0) return 1;
    }
    return 0;
}

static int parse_https_url(const char *url, parsed_url *parsed, char *error, size_t error_size) {
    const char prefix[] = "https://";
    if (strncmp(url, prefix, sizeof(prefix) - 1) != 0) {
        set_error(error, error_size, "ARMv6 OTA permits HTTPS URLs only");
        return -1;
    }
    const char *authority = url + sizeof(prefix) - 1;
    const char *path = strchr(authority, '/');
    const char *end = path == NULL ? authority + strlen(authority) : path;
    size_t host_length = (size_t)(end - authority);
    if (host_length == 0 || host_length >= sizeof(parsed->host) ||
        memchr(authority, '@', host_length) != NULL || memchr(authority, ':', host_length) != NULL) {
        set_error(error, error_size, "invalid ARMv6 OTA HTTPS authority");
        return -1;
    }
    memcpy(parsed->host, authority, host_length);
    parsed->host[host_length] = '\0';
    for (size_t i = 0; i < host_length; ++i) {
        unsigned char value = (unsigned char)parsed->host[i];
        if (!isalnum(value) && value != '.' && value != '-') {
            set_error(error, error_size, "invalid ARMv6 OTA HTTPS host");
            return -1;
        }
        parsed->host[i] = (char)tolower(value);
    }
    if (!allowed_host(parsed->host)) {
        set_error(error, error_size, "ARMv6 OTA HTTPS host is not allowed");
        return -1;
    }
    const char *request_path = path == NULL ? "/" : path;
    if (strlen(request_path) >= sizeof(parsed->path) || strchr(request_path, '#') != NULL) {
        set_error(error, error_size, "invalid ARMv6 OTA HTTPS path");
        return -1;
    }
    for (const unsigned char *cursor = (const unsigned char *)request_path; *cursor != '\0'; ++cursor) {
        if (*cursor <= 0x20 || *cursor == 0x7f) {
            set_error(error, error_size, "invalid ARMv6 OTA HTTPS path");
            return -1;
        }
    }
    strcpy(parsed->path, request_path);
    return 0;
}

static void connection_init(tls_connection *connection) {
    mbedtls_net_init(&connection->net);
    mbedtls_ssl_init(&connection->ssl);
    mbedtls_ssl_config_init(&connection->config);
    mbedtls_x509_crt_init(&connection->roots);
    mbedtls_ctr_drbg_init(&connection->rng);
}

static void connection_free(tls_connection *connection) {
    mbedtls_ssl_close_notify(&connection->ssl);
    mbedtls_net_free(&connection->net);
    mbedtls_ssl_free(&connection->ssl);
    mbedtls_ssl_config_free(&connection->config);
    mbedtls_x509_crt_free(&connection->roots);
    mbedtls_ctr_drbg_free(&connection->rng);
}

static int connect_with_timeout(mbedtls_net_context *net, const char *host,
                                char *error, size_t error_size) {
    struct addrinfo hints;
    struct addrinfo *addresses = NULL;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = IPPROTO_TCP;
    int lookup = getaddrinfo(host, "443", &hints, &addresses);
    if (lookup != 0) {
        snprintf(error, error_size, "cannot resolve ARMv6 OTA HTTPS host: %s", gai_strerror(lookup));
        return -1;
    }
    int last_error = ECONNREFUSED;
    for (struct addrinfo *address = addresses; address != NULL; address = address->ai_next) {
        int fd = socket(address->ai_family, address->ai_socktype, address->ai_protocol);
        if (fd < 0) {
            last_error = errno;
            continue;
        }
        int flags = fcntl(fd, F_GETFL, 0);
        if (flags < 0 || fcntl(fd, F_SETFL, flags | O_NONBLOCK) < 0) {
            last_error = errno;
            close(fd);
            continue;
        }
        int connected = connect(fd, address->ai_addr, address->ai_addrlen);
        if (connected < 0 && errno == EINPROGRESS) {
            fd_set write_fds;
            FD_ZERO(&write_fds);
            FD_SET(fd, &write_fds);
            struct timeval timeout = {15, 0};
            connected = select(fd + 1, NULL, &write_fds, NULL, &timeout);
            if (connected > 0) {
                socklen_t length = sizeof(last_error);
                if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &last_error, &length) == 0 && last_error == 0) connected = 0;
                else connected = -1;
            } else {
                last_error = connected == 0 ? ETIMEDOUT : errno;
                connected = -1;
            }
        } else if (connected < 0) {
            last_error = errno;
        }
        if (connected == 0 && fcntl(fd, F_SETFL, flags) == 0) {
            struct timeval send_timeout = {15, 0};
            setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &send_timeout, sizeof(send_timeout));
            net->fd = fd;
            freeaddrinfo(addresses);
            return 0;
        }
        close(fd);
    }
    freeaddrinfo(addresses);
    snprintf(error, error_size, "cannot connect ARMv6 OTA HTTPS: %s", strerror(last_error));
    return -1;
}

static int connection_open(tls_connection *connection, const parsed_url *url,
                           const unsigned char *ca, size_t ca_length,
                           char *error, size_t error_size) {
    static const unsigned char personal[] = "ove-armv6-ota-tls-v1";
    TRACE("seed");
    int result = mbedtls_ctr_drbg_seed(&connection->rng, system_random,
                                      NULL, personal, sizeof(personal) - 1);
    if (result != 0) {
        set_mbed_error(error, error_size, "cannot seed ARMv6 OTA TLS", result);
        return -1;
    }
    TRACE("parse roots");
    result = mbedtls_x509_crt_parse(&connection->roots, ca, ca_length);
    if (result < 0) {
        set_mbed_error(error, error_size, "cannot parse ARMv6 OTA trust roots", result);
        return -1;
    }
    TRACE("connect");
    if (connect_with_timeout(&connection->net, url->host, error, error_size) != 0) {
        return -1;
    }
    result = mbedtls_ssl_config_defaults(&connection->config, MBEDTLS_SSL_IS_CLIENT,
                                         MBEDTLS_SSL_TRANSPORT_STREAM, MBEDTLS_SSL_PRESET_DEFAULT);
    if (result != 0) {
        set_mbed_error(error, error_size, "cannot configure ARMv6 OTA TLS", result);
        return -1;
    }
    mbedtls_ssl_conf_authmode(&connection->config, MBEDTLS_SSL_VERIFY_REQUIRED);
    mbedtls_ssl_conf_ca_chain(&connection->config, &connection->roots, NULL);
    mbedtls_ssl_conf_rng(&connection->config, mbedtls_ctr_drbg_random, &connection->rng);
    mbedtls_ssl_conf_min_tls_version(&connection->config, MBEDTLS_SSL_VERSION_TLS1_2);
    mbedtls_ssl_conf_max_tls_version(&connection->config, MBEDTLS_SSL_VERSION_TLS1_2);
    mbedtls_ssl_conf_read_timeout(&connection->config, 30000);
    result = mbedtls_ssl_setup(&connection->ssl, &connection->config);
    if (result != 0) {
        set_mbed_error(error, error_size, "cannot set up ARMv6 OTA TLS", result);
        return -1;
    }
    result = mbedtls_ssl_set_hostname(&connection->ssl, url->host);
    if (result != 0) {
        set_mbed_error(error, error_size, "cannot set ARMv6 OTA TLS hostname", result);
        return -1;
    }
    mbedtls_ssl_set_bio(&connection->ssl, &connection->net, mbedtls_net_send,
                        mbedtls_net_recv, mbedtls_net_recv_timeout);
    TRACE("handshake");
    do {
        result = mbedtls_ssl_handshake(&connection->ssl);
    } while (result == MBEDTLS_ERR_SSL_WANT_READ || result == MBEDTLS_ERR_SSL_WANT_WRITE);
    if (result != 0 || mbedtls_ssl_get_verify_result(&connection->ssl) != 0) {
        if (result != 0) set_mbed_error(error, error_size, "ARMv6 OTA TLS handshake failed", result);
        else set_error(error, error_size, "ARMv6 OTA TLS certificate validation failed");
        return -1;
    }
    TRACE("handshake ok");
    return 0;
}

static int ssl_write_all(mbedtls_ssl_context *ssl, const unsigned char *data, size_t length,
                         char *error, size_t error_size) {
    size_t sent = 0;
    while (sent < length) {
        int result = mbedtls_ssl_write(ssl, data + sent, length - sent);
        if (result == MBEDTLS_ERR_SSL_WANT_READ || result == MBEDTLS_ERR_SSL_WANT_WRITE) continue;
        if (result <= 0) {
            set_mbed_error(error, error_size, "cannot write ARMv6 OTA HTTPS request", result);
            return -1;
        }
        sent += (size_t)result;
    }
    return 0;
}

static int reader_fill(reader *input, char *error, size_t error_size) {
    for (;;) {
        int result = mbedtls_ssl_read(input->ssl, input->buffer, sizeof(input->buffer));
        if (result == MBEDTLS_ERR_SSL_WANT_READ || result == MBEDTLS_ERR_SSL_WANT_WRITE) continue;
        if (result == 0 || result == MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY) return 0;
        if (result < 0) {
            set_mbed_error(error, error_size, "cannot read ARMv6 OTA HTTPS response", result);
            return -1;
        }
        input->position = 0;
        input->length = (size_t)result;
        return result;
    }
}

static int reader_byte(reader *input, unsigned char *value, char *error, size_t error_size) {
    if (input->position == input->length) {
        int result = reader_fill(input, error, error_size);
        if (result <= 0) return result;
    }
    *value = input->buffer[input->position++];
    return 1;
}

static int reader_line(reader *input, char *line, size_t line_size, char *error, size_t error_size) {
    size_t length = 0;
    for (;;) {
        unsigned char value;
        int result = reader_byte(input, &value, error, error_size);
        if (result <= 0) {
            if (result == 0) set_error(error, error_size, "truncated ARMv6 OTA HTTPS headers");
            return -1;
        }
        if (value == '\n') {
            if (length > 0 && line[length - 1] == '\r') --length;
            line[length] = '\0';
            return 0;
        }
        if (length + 1 >= line_size) {
            set_error(error, error_size, "ARMv6 OTA HTTPS header line is too large");
            return -1;
        }
        line[length++] = (char)value;
    }
}

static char *trim(char *value) {
    while (*value == ' ' || *value == '\t') ++value;
    char *end = value + strlen(value);
    while (end > value && (end[-1] == ' ' || end[-1] == '\t')) --end;
    *end = '\0';
    return value;
}

static int contains_case_insensitive(const char *value, const char *needle) {
    size_t needle_length = strlen(needle);
    if (needle_length == 0) return 1;
    for (; *value != '\0'; ++value) {
        if (strncasecmp(value, needle, needle_length) == 0) return 1;
    }
    return 0;
}

static int parse_headers(reader *input, response_headers *headers, char *error, size_t error_size) {
    char line[MAX_LINE];
    memset(headers, 0, sizeof(*headers));
    if (reader_line(input, line, sizeof(line), error, error_size) != 0 ||
        sscanf(line, "HTTP/%*u.%*u %d", &headers->status) != 1) {
        set_error(error, error_size, "invalid ARMv6 OTA HTTPS status line");
        return -1;
    }
    size_t total = strlen(line) + 2;
    for (;;) {
        if (reader_line(input, line, sizeof(line), error, error_size) != 0) return -1;
        total += strlen(line) + 2;
        if (total > 65536) {
            set_error(error, error_size, "ARMv6 OTA HTTPS headers are too large");
            return -1;
        }
        if (line[0] == '\0') break;
        char *colon = strchr(line, ':');
        if (colon == NULL) continue;
        *colon = '\0';
        char *value = trim(colon + 1);
        if (strcasecmp(line, "Content-Length") == 0) {
            char *end = NULL;
            errno = 0;
            unsigned long long parsed = strtoull(value, &end, 10);
            if (errno != 0 || end == value || *trim(end) != '\0') {
                set_error(error, error_size, "invalid ARMv6 OTA Content-Length");
                return -1;
            }
            headers->has_length = 1;
            headers->content_length = (uint64_t)parsed;
        } else if (strcasecmp(line, "Transfer-Encoding") == 0 && contains_case_insensitive(value, "chunked")) {
            headers->chunked = 1;
        } else if (strcasecmp(line, "Location") == 0) {
            if (strlen(value) >= sizeof(headers->location)) {
                set_error(error, error_size, "ARMv6 OTA redirect URL is too large");
                return -1;
            }
            strcpy(headers->location, value);
        }
    }
    if (headers->chunked) headers->has_length = 0;
    return 0;
}

static int sink_write(sink *output, const unsigned char *data, size_t length,
                      char *error, size_t error_size) {
    if (length > output->maximum - output->length) {
        set_error(error, error_size, "ARMv6 OTA HTTPS response exceeds the size limit");
        return -1;
    }
    if (output->file != NULL) {
        if (fwrite(data, 1, length, output->file) != length) {
            set_error(error, error_size, "cannot write ARMv6 OTA download");
            return -1;
        }
    } else {
        size_t needed = output->length + length;
        if (needed > output->capacity) {
            size_t capacity = output->capacity == 0 ? 16384 : output->capacity;
            while (capacity < needed) capacity = capacity > output->maximum / 2 ? output->maximum : capacity * 2;
            unsigned char *replacement = (unsigned char *)realloc(output->data, capacity);
            if (replacement == NULL) {
                set_error(error, error_size, "not enough memory for ARMv6 OTA response");
                return -1;
            }
            output->data = replacement;
            output->capacity = capacity;
        }
        memcpy(output->data + output->length, data, length);
    }
    output->length += length;
    return 0;
}

static int copy_exact(reader *input, sink *output, uint64_t length,
                      char *error, size_t error_size) {
    while (length > 0) {
        if (input->position == input->length) {
            int result = reader_fill(input, error, error_size);
            if (result <= 0) {
                if (result == 0) set_error(error, error_size, "truncated ARMv6 OTA HTTPS body");
                return -1;
            }
        }
        size_t available = input->length - input->position;
        size_t take = length < available ? (size_t)length : available;
        if (sink_write(output, input->buffer + input->position, take, error, error_size) != 0) return -1;
        input->position += take;
        length -= take;
    }
    return 0;
}

static int copy_body(reader *input, const response_headers *headers, sink *output,
                     char *error, size_t error_size) {
    if (headers->chunked) {
        char line[MAX_LINE];
        for (;;) {
            if (reader_line(input, line, sizeof(line), error, error_size) != 0) return -1;
            char *extension = strchr(line, ';');
            if (extension != NULL) *extension = '\0';
            char *end = NULL;
            errno = 0;
            unsigned long long chunk = strtoull(trim(line), &end, 16);
            if (errno != 0 || end == line || *trim(end) != '\0') {
                set_error(error, error_size, "invalid ARMv6 OTA HTTPS chunk");
                return -1;
            }
            if (chunk == 0) {
                do {
                    if (reader_line(input, line, sizeof(line), error, error_size) != 0) return -1;
                } while (line[0] != '\0');
                return 0;
            }
            if (copy_exact(input, output, (uint64_t)chunk, error, error_size) != 0) return -1;
            unsigned char cr, lf;
            if (reader_byte(input, &cr, error, error_size) != 1 ||
                reader_byte(input, &lf, error, error_size) != 1 || cr != '\r' || lf != '\n') {
                set_error(error, error_size, "invalid ARMv6 OTA HTTPS chunk terminator");
                return -1;
            }
        }
    }
    if (headers->has_length) return copy_exact(input, output, headers->content_length, error, error_size);
    for (;;) {
        if (input->position < input->length) {
            size_t available = input->length - input->position;
            if (sink_write(output, input->buffer + input->position, available, error, error_size) != 0) return -1;
            input->position = input->length;
        }
        int result = reader_fill(input, error, error_size);
        if (result < 0) return -1;
        if (result == 0) return 0;
    }
}

static int resolve_redirect(const parsed_url *current, const char *location,
                            char *next, size_t next_size, char *error, size_t error_size) {
    if (strncmp(location, "https://", 8) == 0) {
        if (strlen(location) >= next_size) {
            set_error(error, error_size, "ARMv6 OTA redirect URL is too large");
            return -1;
        }
        strcpy(next, location);
        return 0;
    }
    if (location[0] == '/') {
        if (snprintf(next, next_size, "https://%s%s", current->host, location) >= (int)next_size) {
            set_error(error, error_size, "ARMv6 OTA redirect URL is too large");
            return -1;
        }
        return 0;
    }
    set_error(error, error_size, "ARMv6 OTA rejected a non-HTTPS redirect");
    return -1;
}

static int fetch(const unsigned char *ca, size_t ca_length, const char *initial_url,
                 sink *output, char *error, size_t error_size) {
    char url[MAX_URL];
    if (strlen(initial_url) >= sizeof(url)) {
        set_error(error, error_size, "ARMv6 OTA URL is too large");
        return -1;
    }
    strcpy(url, initial_url);
    for (int redirect = 0; redirect <= MAX_REDIRECTS; ++redirect) {
        parsed_url parsed;
        if (parse_https_url(url, &parsed, error, error_size) != 0) return -1;
        TRACE("open connection");
        tls_connection connection;
        connection_init(&connection);
        if (connection_open(&connection, &parsed, ca, ca_length, error, error_size) != 0) {
            connection_free(&connection);
            return -1;
        }
        char request[MAX_PATH + MAX_HOST + 256];
        int request_length = snprintf(request, sizeof(request),
            "GET %s HTTP/1.1\r\nHost: %s\r\nUser-Agent: ove-rs-android-armv6/1\r\n"
            "Accept: application/vnd.github+json, application/octet-stream\r\n"
            "Accept-Encoding: identity\r\nConnection: close\r\n\r\n", parsed.path, parsed.host);
        TRACE("write request");
        if (request_length <= 0 || request_length >= (int)sizeof(request) ||
            ssl_write_all(&connection.ssl, (const unsigned char *)request, (size_t)request_length,
                          error, error_size) != 0) {
            connection_free(&connection);
            return -1;
        }
        reader input;
        memset(&input, 0, sizeof(input));
        input.ssl = &connection.ssl;
        response_headers headers;
        TRACE("read headers");
        if (parse_headers(&input, &headers, error, error_size) != 0) {
            connection_free(&connection);
            return -1;
        }
        if (headers.status == 301 || headers.status == 302 || headers.status == 303 ||
            headers.status == 307 || headers.status == 308) {
            if (redirect == MAX_REDIRECTS || headers.location[0] == '\0' ||
                resolve_redirect(&parsed, headers.location, url, sizeof(url), error, error_size) != 0) {
                if (redirect == MAX_REDIRECTS) set_error(error, error_size, "too many ARMv6 OTA redirects");
                connection_free(&connection);
                return -1;
            }
            connection_free(&connection);
            continue;
        }
        if (headers.status < 200 || headers.status >= 300) {
            snprintf(error, error_size, "GitHub HTTP %d", headers.status);
            connection_free(&connection);
            return -1;
        }
        if (headers.has_length && headers.content_length > output->maximum) {
            set_error(error, error_size, "ARMv6 OTA HTTPS response exceeds the size limit");
            connection_free(&connection);
            return -1;
        }
        TRACE("read body");
        int result = copy_body(&input, &headers, output, error, error_size);
        TRACE("body done");
        connection_free(&connection);
        return result;
    }
    set_error(error, error_size, "too many ARMv6 OTA redirects");
    return -1;
}

static void throw_io(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/io/IOException");
    if (type != NULL) (*env)->ThrowNew(env, type, message);
}

static unsigned char *copy_ca(JNIEnv *env, jbyteArray value, size_t *length) {
    if (value == NULL) return NULL;
    jsize source_length = (*env)->GetArrayLength(env, value);
    if (source_length <= 0 || source_length > 1024 * 1024) return NULL;
    unsigned char *copy = (unsigned char *)malloc((size_t)source_length + 1);
    if (copy == NULL) return NULL;
    (*env)->GetByteArrayRegion(env, value, 0, source_length, (jbyte *)copy);
    copy[source_length] = '\0';
    *length = (size_t)source_length + 1;
    return copy;
}

JNIEXPORT jint JNICALL Java_ru_e6atb_chat_Armv6OtaTls_nativeVersion(
    JNIEnv *env, jclass type) {
    (void)env; (void)type;
    return OTA_TLS_VERSION;
}

JNIEXPORT jbyteArray JNICALL Java_ru_e6atb_chat_Armv6OtaTls_nativeGet(
    JNIEnv *env, jclass type, jbyteArray ca_value, jstring url_value, jint maximum) {
    (void)type;
    if (url_value == NULL || maximum <= 0 || maximum > 16 * 1024 * 1024) {
        throw_io(env, "invalid ARMv6 OTA request");
        return NULL;
    }
    size_t ca_length = 0;
    unsigned char *ca = copy_ca(env, ca_value, &ca_length);
    const char *url = (*env)->GetStringUTFChars(env, url_value, NULL);
    if (ca == NULL || url == NULL) {
        if (url != NULL) (*env)->ReleaseStringUTFChars(env, url_value, url);
        free(ca);
        throw_io(env, "cannot allocate ARMv6 OTA request");
        return NULL;
    }
    sink output;
    memset(&output, 0, sizeof(output));
    output.maximum = (size_t)maximum;
    char error[256];
    int result = fetch(ca, ca_length, url, &output, error, sizeof(error));
    (*env)->ReleaseStringUTFChars(env, url_value, url);
    free(ca);
    if (result != 0) {
        free(output.data);
        throw_io(env, error);
        return NULL;
    }
    jbyteArray response = (*env)->NewByteArray(env, (jsize)output.length);
    if (response != NULL && output.length > 0) {
        (*env)->SetByteArrayRegion(env, response, 0, (jsize)output.length, (const jbyte *)output.data);
    }
    free(output.data);
    return response;
}

JNIEXPORT jlong JNICALL Java_ru_e6atb_chat_Armv6OtaTls_nativeDownload(
    JNIEnv *env, jclass type, jbyteArray ca_value, jstring url_value,
    jstring path_value, jlong maximum) {
    (void)type;
    if (url_value == NULL || path_value == NULL || maximum <= 0) {
        throw_io(env, "invalid ARMv6 OTA download");
        return 0;
    }
    size_t ca_length = 0;
    unsigned char *ca = copy_ca(env, ca_value, &ca_length);
    const char *url = (*env)->GetStringUTFChars(env, url_value, NULL);
    const char *path = (*env)->GetStringUTFChars(env, path_value, NULL);
    if (ca == NULL || url == NULL || path == NULL) {
        if (url != NULL) (*env)->ReleaseStringUTFChars(env, url_value, url);
        if (path != NULL) (*env)->ReleaseStringUTFChars(env, path_value, path);
        free(ca);
        throw_io(env, "cannot allocate ARMv6 OTA download");
        return 0;
    }
    FILE *file = fopen(path, "wb");
    sink output;
    memset(&output, 0, sizeof(output));
    output.maximum = (uint64_t)maximum > SIZE_MAX ? SIZE_MAX : (size_t)maximum;
    output.file = file;
    char error[256];
    int result;
    if (file == NULL) {
        set_error(error, sizeof(error), "cannot create ARMv6 OTA download file");
        result = -1;
    } else {
        result = fetch(ca, ca_length, url, &output, error, sizeof(error));
        if (fclose(file) != 0 && result == 0) {
            set_error(error, sizeof(error), "cannot finish ARMv6 OTA download file");
            result = -1;
        }
    }
    if (result != 0) remove(path);
    (*env)->ReleaseStringUTFChars(env, url_value, url);
    (*env)->ReleaseStringUTFChars(env, path_value, path);
    free(ca);
    if (result != 0) {
        throw_io(env, error);
        return 0;
    }
    return (jlong)output.length;
}

#ifdef OTA_TLS_TEST_MAIN
int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "usage: ota-tls-test CA.pem URL\n");
        return 2;
    }
    FILE *file = fopen(argv[1], "rb");
    if (file == NULL || fseek(file, 0, SEEK_END) != 0) return 2;
    long length = ftell(file);
    if (length <= 0 || length > 1024 * 1024 || fseek(file, 0, SEEK_SET) != 0) return 2;
    unsigned char *ca = (unsigned char *)malloc((size_t)length + 1);
    if (ca == NULL || fread(ca, 1, (size_t)length, file) != (size_t)length) return 2;
    fclose(file);
    ca[length] = '\0';
    sink output;
    memset(&output, 0, sizeof(output));
    output.maximum = 2 * 1024 * 1024;
    char error[256];
    int result = fetch(ca, (size_t)length + 1, argv[2], &output, error, sizeof(error));
    free(ca);
    if (result != 0) {
        fprintf(stderr, "%s\n", error);
        free(output.data);
        return 1;
    }
    fwrite(output.data, 1, output.length, stdout);
    free(output.data);
    return 0;
}
#endif
