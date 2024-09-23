package dev.frozenmilk.mercurial.commands

class MercurialException(override val message: String, override val cause: Throwable) : RuntimeException()