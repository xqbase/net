package com.xqbase.net;

public interface TimerHandler {
	public static interface Closeable extends AutoCloseable {
		@Override
		public void close();
	}

	public Closeable postAtTime(Runnable runnable, long uptime);

	public default Closeable postDelayed(Runnable runnable, long delay) {
		return postAtTime(runnable, System.currentTimeMillis() + delay);
	}

	public default Closeable scheduleAtTime(Runnable runnable, long uptime, long period) {
		long[] times = {0};
		Closeable[] closeable = {null};
		closeable[0] = postAtTime(new Runnable() {
			@Override
			public void run() {
				times[0] ++;
				closeable[0] = postAtTime(this, uptime + times[0] * period);
				// "closeable" may be called in "runnable"
				runnable.run();
			}
		}, uptime);
		// Not equivalent to closeable[0]::close !!!
		return () -> closeable[0].close();
	}

	public default Closeable scheduleDelayed(Runnable runnable, long delay, long period) {
		return scheduleAtTime(runnable, System.currentTimeMillis() + delay, period);
	}
}