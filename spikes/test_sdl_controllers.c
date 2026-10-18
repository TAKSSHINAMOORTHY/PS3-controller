#include <stdio.h>
#include <dlfcn.h>
#include <stdint.h>
#include <unistd.h>

#define SDL_INIT_JOYSTICK 0x00000200u
#define SDL_INIT_GAMEPAD  0x00002000u

typedef uint32_t SDL_InitFlags;
typedef uint32_t SDL_JoystickID;

typedef int (*fn_SDL_Init)(SDL_InitFlags flags);
typedef void (*fn_SDL_Quit)(void);
typedef SDL_JoystickID *(*fn_SDL_GetGamepads)(int *count);
typedef SDL_JoystickID *(*fn_SDL_GetJoysticks)(int *count);
typedef const char *(*fn_SDL_GetGamepadNameForID)(SDL_JoystickID instance_id);
typedef const char *(*fn_SDL_GetJoystickNameForID)(SDL_JoystickID instance_id);
typedef void (*fn_SDL_free)(void *mem);

int main() {
    const char *dylib_path = "/Users/datchu/Downloads/rpcs3-v0.0.43-20336-bd179ffd_macos_aarch64/RPCS3.app/Contents/Frameworks/libSDL3.0.dylib";
    void *handle = dlopen(dylib_path, RTLD_NOW);
    if (!handle) {
        fprintf(stderr, "dlopen failed: %s\n", dlerror());
        return 1;
    }

    fn_SDL_Init p_SDL_Init = (fn_SDL_Init)dlsym(handle, "SDL_Init");
    fn_SDL_Quit p_SDL_Quit = (fn_SDL_Quit)dlsym(handle, "SDL_Quit");
    fn_SDL_GetGamepads p_SDL_GetGamepads = (fn_SDL_GetGamepads)dlsym(handle, "SDL_GetGamepads");
    fn_SDL_GetJoysticks p_SDL_GetJoysticks = (fn_SDL_GetJoysticks)dlsym(handle, "SDL_GetJoysticks");
    fn_SDL_GetGamepadNameForID p_SDL_GetGamepadNameForID = (fn_SDL_GetGamepadNameForID)dlsym(handle, "SDL_GetGamepadNameForID");
    fn_SDL_GetJoystickNameForID p_SDL_GetJoystickNameForID = (fn_SDL_GetJoystickNameForID)dlsym(handle, "SDL_GetJoystickNameForID");
    fn_SDL_free p_SDL_free = (fn_SDL_free)dlsym(handle, "SDL_free");

    if (!p_SDL_Init || !p_SDL_GetGamepads) {
        fprintf(stderr, "dlsym failed for SDL functions!\n");
        return 1;
    }

    if (!p_SDL_Init(SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD)) {
        fprintf(stderr, "SDL_Init failed!\n");
        return 1;
    }

    printf("SDL3 initialized successfully using RPCS3's libSDL3.0.dylib.\n");

    int count = 0;
    SDL_JoystickID *gamepads = p_SDL_GetGamepads(&count);
    printf("Detected Gamepads: %d\n", count);
    if (gamepads) {
        for (int i = 0; i < count; i++) {
            const char *name = p_SDL_GetGamepadNameForID ? p_SDL_GetGamepadNameForID(gamepads[i]) : "Unknown";
            printf("  Gamepad [%d]: ID=%u, Name='%s'\n", i, gamepads[i], name ? name : "NULL");
        }
        if (p_SDL_free) p_SDL_free(gamepads);
    }

    int jcount = 0;
    SDL_JoystickID *joysticks = p_SDL_GetJoysticks(&jcount);
    printf("Detected Joysticks: %d\n", jcount);
    if (joysticks) {
        for (int i = 0; i < jcount; i++) {
            const char *name = p_SDL_GetJoystickNameForID ? p_SDL_GetJoystickNameForID(joysticks[i]) : "Unknown";
            printf("  Joystick [%d]: ID=%u, Name='%s'\n", i, joysticks[i], name ? name : "NULL");
        }
        if (p_SDL_free) p_SDL_free(joysticks);
    }

    p_SDL_Quit();
    dlclose(handle);
    return 0;
}
