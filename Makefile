.PHONY: help mac android test clean

help:
	@echo "🎮 PS3 Controller for macOS & RPCS3"
	@echo "===================================="
	@echo "1-Command Setup:"
	@echo "  make mac       - Build, install to /Applications, verify permissions, and launch Mac Companion"
	@echo "  make android   - Build APK, configure USB reverse tunnel, install, and launch Android Controller"
	@echo "  make test      - Run all macOS and Android automated test suites"
	@echo "  make clean     - Clean build artifacts"

mac:
	./setup-mac.sh

android:
	./setup-android.sh

test:
	@echo "Running Mac unit tests..."
	cd MacApp && swift test
	@echo "Running Android unit tests..."
	cd AndroidApp && ./gradlew testDebugUnitTest

clean:
	cd MacApp && swift package clean
	cd AndroidApp && ./gradlew clean
