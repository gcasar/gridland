package si.evil.server;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Accurate to at most 15ms intervals
 * 
 * Modified from SGP IntervalManager
 * 
 * @author Gregor
 *
 */
abstract class IntervalManager {
	class TickerThread extends TimerTask{
		IntervalManager.Listener listener;
		TickerThread(IntervalManager.Listener l){
			this.listener = l;
		}

		@Override
		public void run() {
			listener.onTick();
		}		
	}
	
	interface Listener{
		void onTick();
	}
	
	protected Listener listener = null;
	
	/**
	 * Handle to the TickerThread
	 */
	protected TickerThread ticker = null;
	
	/**
	 * Needed to change the interval
	 */
	protected Timer timer = null;
	
	/**
	 * Interval timeout in nanoseconds
	 */
	protected long timeout;
	
	IntervalManager(Listener listener){
		this.listener = listener;
	}
	
	long getTimeout(){
		return timeout;
	}

	void start(long t) {
		this.timeout = t;
		t/=1000;//From nanoseconds to milliseconds
		
		if(timer!=null)
			timer = new Timer();
		
		ticker = new TickerThread(this.listener);
		timer.scheduleAtFixedRate(ticker, 0, t);
	}

	void changeInterval(long t) {
		end();
		start(t);
	}

	void end() {
		ticker.cancel();
		timer.cancel();
	}
}
