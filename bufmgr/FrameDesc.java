package bufmgr;

import global.Page;
import global.PageId;

public class FrameDesc extends Page {

	
	protected boolean dirty;
	protected boolean valid; 
	protected PageId pageno; 
	protected int pin_count; 
	protected boolean refbit;
	
	public FrameDesc() {
		dirty = false;
		valid = false; 
		pageno = null; 
		pin_count = 0;
		refbit = false;
		
	}

}
