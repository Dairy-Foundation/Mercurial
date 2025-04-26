package dev.frozenmilk.dairy.mercurial.continuations

class FiberException(
    override val cause: Throwable,
    k: Continuation,
) : RuntimeException(cause) {
    init {
        k.stackTrace?.let { stackTrace ->
            var relevantCount = 0
            val causeLen = cause.stackTrace.size
            while (relevantCount < causeLen) {
                val stackTraceElement = cause.stackTrace[relevantCount]
                if (stackTraceElement.className == INTERSECT_CLASS_NAME && INTERSECT_METHOD_NAMES.contains(stackTraceElement.methodName)) break
                relevantCount++
            }
            val res = arrayOfNulls<StackTraceElement>(relevantCount + stackTrace.size)
            System.arraycopy(cause.stackTrace, 0, res, 0, relevantCount)
            System.arraycopy(stackTrace, 0, res, relevantCount, stackTrace.size)
            this.stackTrace = res
        }
    }

    companion object {
        private val INTERSECT_CLASS_NAME = Fiber.Companion::class.java.name
        private val INTERSECT_METHOD_NAMES = arrayOf("UNRAVEL", "CANCEL")
    }
}
