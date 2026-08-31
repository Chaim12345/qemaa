package com.example.engine

/**
 * Virtual machine states.
 */
enum class VmState {
    STOPPED,
    BOOTING,
    RUNNING,
    ERROR
}

/**
 * How the guest is booted.
 */
enum class BootMode {
    /** Prebuilt persistent distro image (Node, Go, agents) on a virtio disk. */
    DISTRO,

    /** Alpine netboot into RAM — the no-download fallback. */
    NETBOOT
}
