package bufmgr;

import global.GlobalConst;
import global.PageId;

import java.util.*;


public class Clock implements GlobalConst {

	protected List<FrameDesc> frametab;
	protected int current;
	 
	public Clock() {

		this.frametab = null;
		this.current = 0;
	}



    public FrameDesc pickVictim(BufMgr bm) {

        this.frametab = new ArrayList<FrameDesc>(bm.bufmap.values());

        
        for (int i = 0; i < (frametab.size() * 2); i++) {
            
            if (frametab.get(current).valid != true) {
                return frametab.get(current);
            }
           
            else {
                if (frametab.get(current).pin_count == 0) {
                    
                    if (frametab.get(current).refbit) {
                        frametab.get(current).refbit = false;
                    } else {
                        return frametab.get(current);
                    }
                }
            }
            
            
         	current = (current+1) % frametab.size();
            
        }

       
        return null;
    }
}