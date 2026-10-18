#include <stdio.h>
#include <dlfcn.h>
#include <stdint.h>
#include <unistd.h>
#include <stdbool.h>

#define SDL_INIT_JOYSTICK 0x00000200u
#define SDL_INIT_GAMEPAD  0x00002000u

typedef uint32_t SDL_InitFlags;
typedef uint32_t SDL_JoystickID;

typedef struct SDL_Event {
    uint32_t type;
    uint32_t reserved;
    uint64_t timestamp;
    uint8_t padding[112];
} SDL_Event;

typedef int (*fn_SDL_Init)(SDL_InitFlags flags);
typedef void (*fn_SDL_Quit)(void);
typedef void *(*fn_SDL_OpenJoystick)(SDL_JoystickID instance_id);
typedef void (*fn_SDL_CloseJoystick)(void *joystick);
typedef SDL_JoystickID *(*fn_SDL_GetJoysticks)(int *count);
typedef const char *(*fn_SDL_GetJoystickNameForID)(SDL_JoystickID instance_id);
typedef bool (*fn_SDL_PollEvent)(SDL_Event *event);
typedef void (*fn_SDL_PumpEvents)(void);
typedef uint8_t (*fn_SDL_GetJoystickButton)(void *joystick, int button);
typedef int16_t (*fn_SDL_GetJoystickAxis)(void *joystick, int axis);
typedef void (*fn_SDL_free)(void *mem);

int main(int argc, char *argv[]) {
    const char *dylib_path = "/Users/datchu/Downloads/rpcs3-v0.0.43-20336-bd179ffd_macos_aarch64/RPCS3.app/Contents/Frameworks/libSDL3.0.dylib";
    void *handle = dlopen(dylib_path, RTLD_NOW);
    if (!handle) {
        fprintf(stderr, "dlopen failed: %s\n", dlerror());
        return 1;
    }

    fn_SDL_Init p_SDL_Init = (fn_SDL_Init)dlsym(handle, "SDL_Init");
    fn_SDL_Quit p_SDL_Quit = (fn_SDL_Quit)dlsym(handle, "SDL_Quit");
    fn_SDL_OpenJoystick p_SDL_OpenJoystick = (fn_SDL_OpenJoystick)dlsym(handle, "SDL_OpenJoystick");
    fn_SDL_CloseJoystick p_SDL_CloseJoystick = (fn_SDL_CloseJoystick)dlsym(handle, "SDL_CloseJoystick");
    fn_SDL_GetJoysticks p_SDL_GetJoysticks = (fn_SDL_GetJoysticks)dlsym(handle, "SDL_GetJoysticks");
    fn_SDL_GetJoystickNameForID p_SDL_GetJoystickNameForID = (fn_SDL_GetJoystickNameForID)dlsym(handle, "SDL_GetJoystickNameForID");
    fn_SDL_PollEvent p_SDL_PollEvent = (fn_SDL_PollEvent)dlsym(handle, "SDL_PollEvent");
    fn_SDL_PumpEvents p_SDL_PumpEvents = (fn_SDL_PumpEvents)dlsym(handle, "SDL_PumpEvents");
    fn_SDL_GetJoystickButton p_SDL_GetJoystickButton = (fn_SDL_GetJoystickButton)dlsym(handle, "SDL_GetJoystickButton");
    fn_SDL_GetJoystickAxis p_SDL_GetJoystickAxis = (fn_SDL_GetJoystickAxis)dlsym(handle, "SDL_GetJoystickAxis");
    fn_SDL_free p_SDL_free = (fn_SDL_free)dlsym(handle, "SDL_free");

    if (!p_SDL_Init || !p_SDL_PollEvent) {
        fprintf(stderr, "Failed to resolve symbols from libSDL3.0.dylib\n");
        return 1;
    }

    if (!p_SDL_Init(SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD)) {
        fprintf(stderr, "SDL_Init failed\n");
        return 1;
    }

    printf("MONITOR: SDL3 initialized. Monitoring events for 15 seconds...\n");
    fflush(stdout);

    void *active_joystick = NULL;

    for (int sec = 0; sec < 150; sec++) {
        p_SDL_PumpEvents();
        SDL_Event ev;
        while (p_SDL_PollEvent(&ev)) {
            printf("EVENT: type=0x%x (%u)\n", ev.type, ev.type);
            fflush(stdout);
            // Check for added (0x605 = 1541)
            if (ev.type == 0x605 || ev.type == 1541) {
                uint32_t jid = *(uint32_t*)(((uint8_t*)&ev) + 16);
                printf("  -> JOYSTICK_ADDED: ID=%u\n", jid);
                if (!active_joystick && p_SDL_OpenJoystick) {
                    active_joystick = p_SDL_OpenJoystick(jid);
                    printf("  -> Opened joystick: %p\n", active_joystick);
                }
            } else if (ev.type == 0x603 || ev.type == 1539) {
                // Button Down
                uint8_t btn = *(((uint8_t*)&ev) + 20);
                uint8_t state = *(((uint8_t*)&ev) + 21);
                printf("  -> JOYSTICK_BUTTON_DOWN: button=%u, state=%u\n", btn, state);
            } else if (ev.type == 0x604 || ev.type == 1540) {
                // Button Up
                uint8_t btn = *(((uint8_t*)&ev) + 20);
                uint8_t state = *(((uint8_t*)&ev) + 21);
                printf("  -> JOYSTICK_BUTTON_UP: button=%u, state=%u\n", btn, state);
            } else if (ev.type == 0x600 || ev.type == 1536) {
                // Axis motion
                uint8_t axis = *(((uint8_t*)&ev) + 20);
                int16_t value = *(int16_t*)(((uint8_t*)&ev) + 22);
                printf("  -> JOYSTICK_AXIS_MOTION: axis=%u, value=%d\n", axis, value);
            }
            fflush(stdout);
        }

        if (active_joystick && p_SDL_GetJoystickButton) {
            for (int b = 0; b < 16; b++) {
                if (p_SDL_GetJoystickButton(active_joystick, b)) {
                    printf("POLL: Joystick Button %d is currently PRESSED (1)\n", b);
                    fflush(stdout);
                }
            }
        }
        usleep(100000); // 100ms
    }

    if (active_joystick && p_SDL_CloseJoystick) {
        p_SDL_CloseJoystick(active_joystick);
    }

    p_SDL_Quit();
    dlclose(handle);
    printf("MONITOR: Finished.\n");
    return 0;
}
