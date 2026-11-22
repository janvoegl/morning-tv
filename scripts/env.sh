# Source this file: sets up the toolchain installed by scripts/setup-toolchain.sh
MTV_OPT="${MTV_OPT:-$HOME/.local/opt}"
export JAVA_HOME="$MTV_OPT/jdk-21"
export ANDROID_HOME="$MTV_OPT/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
