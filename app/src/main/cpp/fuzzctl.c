#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

static int allowed_axis(long axis) {
    return axis == ABS_MT_POSITION_X || axis == ABS_MT_POSITION_Y;
}

static int parse_long(const char *text, long *value) {
    char *end = NULL;
    errno = 0;
    long parsed = strtol(text, &end, 0);
    if (errno || !end || *end != '\0') return -1;
    *value = parsed;
    return 0;
}

static void usage(const char *name) {
    fprintf(stderr, "Usage: %s DEVICE AXIS [FUZZ]\n", name);
    fprintf(stderr, "AXIS must be 0x35 (X) or 0x36 (Y). Without FUZZ, axis metadata is printed.\n");
}

int main(int argc, char **argv) {
    if (argc != 3 && argc != 4) { usage(argv[0]); return 2; }
    long axis = 0;
    if (parse_long(argv[2], &axis) || !allowed_axis(axis)) {
        fprintf(stderr, "Error: unsupported axis; only 0x35 and 0x36 are allowed.\n");
        return 2;
    }
    int flags = argc == 4 ? O_RDWR : O_RDONLY;
    int fd = open(argv[1], flags | O_CLOEXEC);
    if (fd < 0) { fprintf(stderr, "Error: cannot open %s: %s\n", argv[1], strerror(errno)); return 1; }
    struct input_absinfo info;
    if (ioctl(fd, EVIOCGABS((int)axis), &info) < 0) {
        fprintf(stderr, "Error: EVIOCGABS failed: %s\n", strerror(errno)); close(fd); return 1;
    }
    if (argc == 4) {
        long fuzz = 0;
        if (parse_long(argv[3], &fuzz) || fuzz < 0 || fuzz > 65535) {
            fprintf(stderr, "Error: FUZZ must be between 0 and 65535.\n"); close(fd); return 2;
        }
        info.fuzz = (int)fuzz;
        if (ioctl(fd, EVIOCSABS((int)axis), &info) < 0) {
            fprintf(stderr, "Error: EVIOCSABS failed: %s\n", strerror(errno)); close(fd); return 1;
        }
        if (ioctl(fd, EVIOCGABS((int)axis), &info) < 0) {
            fprintf(stderr, "Error: verification read failed: %s\n", strerror(errno)); close(fd); return 1;
        }
    }
    printf("axis=0x%02lx value=%d min=%d max=%d fuzz=%d flat=%d resolution=%d\n",
           axis, info.value, info.minimum, info.maximum, info.fuzz, info.flat, info.resolution);
    close(fd);
    return 0;
}
