SUMMARY = "Android platform tools: adbd, and adb/fastboot for the build host"
DESCRIPTION = "adbd for the target; adb, fastboot, img2simg, simg2img and \
mkbootimg as native tools. Built from AOSP sources with a make-based build \
in place of Soong."
HOMEPAGE = "https://developer.android.com/tools/releases/platform-tools"
SECTION = "console/utils"
# What's compiled: Apache-2.0 for nearly all of it; BSD-2-Clause in fastboot
# and libcutils' sockets.cpp; ISC for libcutils' strlcpy.c; MIT for the
# bundled fmtlib, linked into libbase.
LICENSE = "Apache-2.0 AND BSD-2-Clause AND ISC AND MIT"
LIC_FILES_CHKSUM = " \
    file://packages/modules/adb/NOTICE;md5=b8b9862d565b7b0106106ac3ea42fab6 \
    file://system/core/fastboot/fastboot.cpp;beginline=1;endline=27;md5=b058d490dbbeb4d350e3a04f7bdb7444 \
    file://system/core/libcutils/strlcpy.c;beginline=1;endline=15;md5=f800c066486863c8a78974152f69b5ed \
    file://external/fmtlib/LICENSE;md5=b9257785fc4f3803a4b71b76c1412729 \
"

# The AOSP projects the build uses, straight from android.googlesource.com at
# one release tag, laid out at their AOSP paths under ${S}; the make-based
# build in place of Soong comes from this layer.
SRC_URI = " \
    file://build;subdir=${BP} \
    file://android-tools-adbd.service \
"
AOSP_TAG = "android-17.0.0_r1"
AOSP_PROJECTS = " \
    packages/modules/adb \
    system/core \
    system/libbase \
    system/logging \
    system/libziparchive \
    system/extras \
    system/fs/fs_mgr \
    system/tools/mkbootimg \
    external/avb \
    external/fmtlib \
    frameworks/native \
"
inherit aosp-projects
require ${BPN}-aosp-srcrevs.inc
require android-tools-aosp-patches.inc
S = "${UNPACKDIR}/${BP}"

DEPENDS = "android-libboringssl brotli libcap libusb1 lz4 protobuf protobuf-native zlib zstd"
# libziparchive and fastboot (native only) include <gtest/gtest_prod.h> for
# FRIEND_TEST; header only, nothing links it.
DEPENDS:append:class-native = " googletest"

inherit systemd useradd

COMPATIBLE_HOST:powerpc = "(null)"
COMPATIBLE_HOST:powerpc64 = "(null)"
COMPATIBLE_HOST:powerpc64le = "(null)"
COMPATIBLE_HOST:riscv32 = "(null)"

# Thumb-1 can't build this; build armv4/armv5 as ARM.
ARM_INSTRUCTION_SET:armv4 = "arm"
ARM_INSTRUCTION_SET:armv5 = "arm"

CFLAGS:append = " -fPIC"
CXXFLAGS:append = " -fPIC -std=gnu++23 -D_Nonnull= -D_Nullable= -I${STAGING_INCDIR}/boringssl"
LDFLAGS:append = " -L${STAGING_LIBDIR}/android"
# libbase's endian.h and threads.h take their musl branch on this define,
# which Soong sets; without it they fall through to the Windows one.
CFLAGS:append:libc-musl = " -DANDROID_HOST_MUSL"
CXXFLAGS:append:libc-musl = " -DANDROID_HOST_MUSL"
# The native tools are meant to work outside the build too, so they use the
# host's dynamic linker rather than uninative's.
LDFLAGS:remove:class-native = "-Wl,--dynamic-linker=${UNINATIVE_LOADER}"

PACKAGECONFIG ??= "${@bb.utils.filter('DISTRO_FEATURES', 'systemd', d)}"
PACKAGECONFIG[systemd] = "HAVE_SYSTEMD=1,,systemd"
# Apply ADBD_ROOT_SECLABEL with SELinux (setcon) when it's enabled. Without
# this, the label is applied with SMACK if that's active.
PACKAGECONFIG[selinux] = "HAVE_SELINUX=1,,libselinux"
# Apply Android's fs_config ownership/mode tables to files pushed to /system,
# /vendor and /oem. They describe Android partition images, not a Linux rootfs.
PACKAGECONFIG[fs-config] = ""
CXXFLAGS:append = "${@bb.utils.contains('PACKAGECONFIG', 'fs-config', ' -DADBD_USE_FS_CONFIG', '', d)}"

# adbd's root and authentication policy is Android's, from properties:
#   ro.secure=0       run as root (default 1: drop to ADBD_USER)
#   ro.debuggable=1   allow `adb root`, and connections without a key
#   ro.adb.secure=1   ...but still require a key when debuggable
# ro.* come from these build.prop-style files, later files winning; with
# none of them set, adbd runs as ADBD_USER and every host needs a key.
ADBD_PROPERTY_FILES ?= "${sysconfdir}/build.prop:/run/build.prop"
# Other properties (service.adb.root, ...) live here, one file each.
ADBD_PROPERTY_DIR ?= "/run/adbd/properties"
# The security context (SELinux) or label (SMACK) adbd runs in after
# `adb root`; added to the systemd unit as --root_seclabel. Other init systems
# pass --root_seclabel=<context> themselves. Empty: adbd's own.
ADBD_ROOT_SECLABEL ?= ""
# The user adbd drops to (Android's AID_SHELL).
ADBD_USER ?= "adb"
# `adb remount` runs this, as the adbd user (root after `adb root`).
ADBD_REMOUNT_COMMAND ?= "remount"
# Where adbd reads trusted host keys, colon-separated. Empty: Android's
# /adb_keys and /data/misc/adb/adb_keys.
ADBD_AUTH_KEY_PATHS ?= ""
# Shown by `adb version` and `fastboot --version` in place of Android's build
# number.
ADB_BUILD_ID ?= "${PV}"

EXTRA_OEMAKE = " \
    -f build/Makefile \
    SYSROOT=${STAGING_DIR_HOST} \
    PROTOC=${STAGING_BINDIR_NATIVE}/protoc \
    ADB_BUILD_ID=${ADB_BUILD_ID} \
    ADBD_PROPERTY_FILES=${ADBD_PROPERTY_FILES} \
    ADBD_PROPERTY_DIR=${ADBD_PROPERTY_DIR} \
    ADBD_USER=${ADBD_USER} \
    ADBD_REBOOT_COMMAND=${libexecdir}/adbd/reboot \
    ADBD_REMOUNT_COMMAND=${ADBD_REMOUNT_COMMAND} \
    ${PACKAGECONFIG_CONFARGS} \
    ${@'ADBD_AUTH_KEY_PATHS=' + d.getVar('ADBD_AUTH_KEY_PATHS') if d.getVar('ADBD_AUTH_KEY_PATHS') else ''} \
"

# development/ (only for Pkg.Revision) is not fetched; the version is PV.
EXTRA_OEMAKE += "PLATFORM_TOOLS_VERSION=${PV}"

ANDROID_TOOLS_MAKE_TARGET = "adbd"
ANDROID_TOOLS_MAKE_TARGET:class-native = "host-tools"

do_compile() {
    oe_runmake ${ANDROID_TOOLS_MAKE_TARGET}
}

do_install() {
    install -D -m 0755 ${B}/out/adbd ${D}${bindir}/adbd
    install -D -m 0755 ${B}/out/adbd-reboot ${D}${libexecdir}/adbd/reboot
    install -D -m 0644 ${UNPACKDIR}/android-tools-adbd.service \
        ${D}${systemd_system_unitdir}/android-tools-adbd.service
    # adbd only tells systemd it's ready when built with the systemd option.
    if ${@bb.utils.contains('PACKAGECONFIG', 'systemd', 'false', 'true', d)}; then
        sed -i 's|^Type=notify$|Type=simple|' \
            ${D}${systemd_system_unitdir}/android-tools-adbd.service
    fi
    if [ -n "${ADBD_ROOT_SECLABEL}" ]; then
        sed -i 's|^ExecStart=/usr/bin/adbd$|& --root_seclabel=${ADBD_ROOT_SECLABEL}|' \
            ${D}${systemd_system_unitdir}/android-tools-adbd.service
    fi
    install -d ${D}${libdir}/android
    install -m 0755 ${B}/out/lib*.so.0 ${D}${libdir}/android/
}

do_install:class-native() {
    install -d ${D}${bindir} ${D}${libdir}/android ${D}${includedir}/sparse
    for tool in adb fastboot img2simg simg2img; do
        install -m 0755 ${B}/out/$tool ${D}${bindir}/
    done
    install -m 0755 ${S}/system/tools/mkbootimg/mkbootimg.py \
        ${S}/system/tools/mkbootimg/unpack_bootimg.py ${D}${bindir}/
    install -m 0755 ${B}/out/lib*.so.0 ${D}${libdir}/android/

    # libsparse, and the libraries it links against, also go on the default
    # search path with libsparse's headers, for e2fsprogs-native.
    for lib in libsparse libbase liblog; do
        mv ${D}${libdir}/android/$lib.so.0 ${D}${libdir}/
        ln -s $lib.so.0 ${D}${libdir}/$lib.so
        ln -s ../$lib.so.0 ${D}${libdir}/android/$lib.so.0
        ln -s ../$lib.so ${D}${libdir}/android/$lib.so
    done
    cp -R ${S}/system/core/libsparse/include/sparse/. ${D}${includedir}/sparse/
}

PACKAGES =+ "${PN}-adbd"

SYSTEMD_PACKAGES = "${PN}-adbd"
SYSTEMD_SERVICE:${PN}-adbd = "android-tools-adbd.service"

USERADD_PACKAGES = "${PN}-adbd"
USERADD_PARAM:${PN}-adbd = "--system --no-create-home --shell /bin/false --user-group ${ADBD_USER}"

FILES:${PN} += "${libdir}/android"
FILES:${PN}-adbd = " \
    ${bindir}/adbd \
    ${libexecdir}/adbd \
    ${systemd_system_unitdir}/android-tools-adbd.service \
"
# adbd's libraries live in ${libdir}/android and are found via its rpath.
PRIVATE_LIBS:${PN} = "liblog.so.0 libbase.so.0 libcutils.so.0"

RDEPENDS:${PN}:class-target = "android-tools-conf-configfs android-libboringssl"
RDEPENDS:${PN}-adbd += "${PN}"
RRECOMMENDS:${PN}-adbd += "kernel-module-libcomposite kernel-module-usb-f-fs"

BBCLASSEXTEND = "native"
