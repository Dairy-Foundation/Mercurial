package dev.frozenmilk.mercurial.commands

@FunctionalInterface
fun interface StackUnwinder {
	fun unwindStack(command: Command, substitutedSymbol: String): String
}