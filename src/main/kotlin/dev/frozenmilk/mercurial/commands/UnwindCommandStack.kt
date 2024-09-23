package dev.frozenmilk.mercurial.commands

class UnwindCommandStack (val causeCommand: Command, parent: Command, val phase: String, message: String, override val cause: Throwable) : RuntimeException() {
	override var message = message
		private set
	var parent: Command = parent
		private set
	fun wrapAndRethrow(context: Command): Nothing {
		message = context.unwindStackTrace(parent, message)
		parent = context
		throw this
	}
}