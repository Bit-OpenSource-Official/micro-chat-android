FROM gradle:8.10.2-jdk17 AS build

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    CMDLINE_TOOLS=/opt/android-sdk/cmdline-tools/latest

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/android-sdk/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/android-tools \
    && mv /tmp/android-tools/cmdline-tools "$CMDLINE_TOOLS" \
    && rm -rf /tmp/cmdline-tools.zip /tmp/android-tools

ENV PATH="$PATH:$CMDLINE_TOOLS/bin:$ANDROID_HOME/platform-tools"

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-35" "build-tools;34.0.0"

WORKDIR /src
COPY settings.gradle build.gradle ./
COPY app/build.gradle app/proguard-rules.pro app/
RUN gradle --no-daemon :app:dependencies >/dev/null || true

COPY app app
RUN gradle --no-daemon :app:assembleRelease
