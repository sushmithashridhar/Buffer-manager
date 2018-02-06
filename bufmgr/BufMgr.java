package bufmgr;

import global.GlobalConst;
import global.Minibase;
import global.Page;
import global.PageId;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * <h3>Minibase Buffer Manager</h3>
 * The buffer manager manages an array of main memory pages.  The array is
 * called the buffer pool, each page is called a frame.  
 * It provides the following services:
 * <ol>
 * <li>Pinning and unpinning disk pages to/from frames
 * <li>Allocating and deallocating runs of disk pages and coordinating this with
 * the buffer pool
 * <li>Flushing pages from the buffer pool
 * <li>Getting relevant data
 * </ol>
 * The buffer manager is used by access methods, heap files, and
 * relational operators.
 */
public class BufMgr implements GlobalConst {

  /**
   * Constructs a buffer manager by initializing member data.  
   * 
   * @param numframes number of frames in the buffer pool
   */
	
	ArrayList<FrameDesc> bufferpool;
	HashMap<PageId, FrameDesc> bufmap;
	Clock clock;
	
  public BufMgr(int numframes) {

	
	  
	  bufferpool = new ArrayList<FrameDesc>();
	  
	  for (int i = 0; i < numframes; ++i) {
		  bufferpool.add(new FrameDesc());
	  }

	  bufmap = new HashMap<PageId, FrameDesc>();
	  clock = new Clock();
	  
  } 

  /**
   * The result of this call is that disk page number pageno should reside in
   * a frame in the buffer pool and have an additional pin assigned to it, 
   * and mempage should refer to the contents of that frame. <br><br>
   * 
   * If disk page pageno is already in the buffer pool, this simply increments 
   * the pin count.  Otherwise, this<br> 
   * <pre>
   * 	uses the replacement policy to select a frame to replace
   * 	writes the frame's contents to disk if valid and dirty
   * 	if (contents == PIN_DISKIO)
   * 		read disk page pageno into chosen frame
   * 	else (contents == PIN_MEMCPY)
   * 		copy mempage into chosen frame
   * 	[omitted from the above is maintenance of the frame table and hash map]
   * </pre>		
   * @param pageno identifies the page to pin
   * @param mempage An output parameter referring to the chosen frame.  If
   * contents==PIN_MEMCPY it is also an input parameter which is copied into
   * the chosen frame, see the contents parameter. 
   * @param contents Describes how the contents of the frame are determined.<br>  
   * If PIN_DISKIO, read the page from disk into the frame.<br>  
   * If PIN_MEMCPY, copy mempage into the frame.<br>  
   * If PIN_NOOP, copy nothing into the frame - the frame contents are irrelevant.<br>
   * Note: In the cases of PIN_MEMCPY and PIN_NOOP, disk I/O is avoided.
   * @throws IllegalArgumentException if PIN_MEMCPY and the page is pinned.
   * @throws IllegalStateException if all pages are pinned (i.e. pool is full)
   */
  public void pinPage(PageId pageno, Page mempage, int contents) {

	
	  FrameDesc frameinfo = bufmap.get(pageno);
		  
	
	  if(frameinfo != null) {
          frameinfo.pin_count++;

      } else {
		  FrameDesc victimFrm;
		  
		  if (!bufferpool.isEmpty()) {
			  victimFrm = bufferpool.get(bufferpool.size()-1);
			  bufferpool.remove(victimFrm);
		  } else {
			  victimFrm = clock.pickVictim(this);

			  
			  if (victimFrm == null) {
				  throw new IllegalStateException("All pages are pinned (pool is full)!");
			  } else {
				  
				  if (victimFrm.dirty) {
					  flushPage(pageno, victimFrm);
				  }
			  }

			 
			  bufmap.remove(pageno);

			 
			  victimFrm.pin_count = 0;
			  victimFrm.valid = false;
			  victimFrm.dirty = false;
			  victimFrm.refbit = false;
		  }

			  
			  if(contents == PIN_DISKIO) {
				
				 
		            
		          Minibase.DiskManager.read_page(pageno, victimFrm);
		    
		          victimFrm.pin_count ++;
		          victimFrm.valid = true;
		          victimFrm.dirty = false;
				  victimFrm.pageno = new PageId();
		          victimFrm.pageno.copyPageId(pageno);
		          victimFrm.refbit = true;
		          
		          bufmap.put(victimFrm.pageno, victimFrm);
		          mempage.setData(victimFrm.getData());
			  }
			  else if (contents == PIN_MEMCPY) {
				
				  
				  victimFrm.pin_count++;
		          victimFrm.valid = true;
		          victimFrm.dirty = false;
				  victimFrm.pageno = new PageId();
		          victimFrm.pageno.copyPageId(pageno);
		          victimFrm.refbit = true;
	              

		          bufmap.put(victimFrm.pageno, victimFrm);
		          victimFrm.setPage(mempage);
			  }
			  else if(contents == PIN_NOOP) {
				
                  victimFrm.pin_count++;
                  victimFrm.valid = true;
                  victimFrm.dirty = false;
                  victimFrm.pageno = new PageId(pageno.pid);
                  victimFrm.refbit = true;

                  bufmap.put(pageno, victimFrm);
			  }
			  }
		  }
	  
 // } 
  
  /**
   * Unpins a disk page from the buffer pool, decreasing its pin count.
   * 
   * @param pageno identifies the page to unpin
   * @param dirty UNPIN_DIRTY if the page was modified, UNPIN_CLEAN otherwise
   * @throws IllegalArgumentException if the page is not in the buffer pool
   *  or not pinned
   */
  public void unpinPage(PageId pageno, boolean dirty) {

		  
		  FrameDesc frameinfo = bufmap.get(pageno);
		  
		  
		  if(frameinfo == null || frameinfo.pin_count == 0)
			{
			  throw new IllegalArgumentException("Page is not in the buffer pool or not pinned!"+ "P###"
					  + pageno.toString() + ":" + pageno.pid);
			}
			else
			{
				
				if(dirty)
					frameinfo.dirty = UNPIN_DIRTY; 
				else
					frameinfo.dirty = UNPIN_CLEAN; 
				frameinfo.pin_count--;
				
				if (frameinfo.pin_count == 0)
			      {
			        frameinfo.refbit = true;
			      }
			}
  } 
  
  /**
   * Allocates a run of new disk pages and pins the first one in the buffer pool.
   * The pin will be made using PIN_MEMCPY.  Watch out for disk page leaks.
   * 
   * @param firstpg input and output: holds the contents of the first allocated page
   * and refers to the frame where it resides
   * @param run_size input: number of pages to allocate
   * @return page id of the first allocated page
   * @throws IllegalArgumentException if firstpg is already pinned
   * @throws IllegalStateException if all pages are pinned (i.e. pool exceeded)
   */
  public PageId newPage(Page firstpg, int run_size) {

	  
	  PageId pageno = new PageId();

	  FrameDesc frameinfo = bufmap.get(pageno);

	  if(getNumUnpinned() == 0) {
		  throw new IllegalStateException("All pages are pinned!");
	  }
	  
	  else if (frameinfo != null && frameinfo.pin_count > 0) {
		  throw new IllegalArgumentException("firstpg is already pinned!");
	  }
	 
	  else {
		  pageno.pid = Minibase.DiskManager.allocate_page(run_size).pid;
		  pinPage(pageno, firstpg, PIN_MEMCPY);
	  }
	  
	 
	  return pageno;

  } 

  /**
   * Deallocates a single page from disk, freeing it from the pool if needed.
   * 
   * @param pageno identifies the page to remove
   * @throws IllegalArgumentException if the page is pinned
   */
  public void freePage(PageId pageno) {

		  
		
		FrameDesc frameinfo = bufmap.get(pageno);
	    
		if(frameinfo != null && frameinfo.pin_count > 0) {
			throw new IllegalArgumentException("The page is pinned!");
		}
		
		else {
			if(bufmap.containsKey(pageno.pid)) {
				bufmap.remove(pageno);
			}
			Minibase.DiskManager.deallocate_page(pageno);
			
		}

  } 

  /**
   * Write all valid and dirty frames to disk.
   * Note flushing involves only writing, not unpinning or freeing
   * or the like.
   * 
   */
  public void flushAllFrames() {

	  Iterator map = bufmap.entrySet().iterator();
	  while (map.hasNext()) {
		  Map.Entry pair = (Map.Entry) map.next();
		  PageId key = (PageId) pair.getKey();
		  FrameDesc value = (FrameDesc) pair.getValue();
		  map.remove();
		  if (value.valid && value.dirty) {
			  flushPage(key, value);
		  }
	  }
	  } 

  /**
   * Write a page in the buffer pool to disk, if dirty.
   * 
   * @throws IllegalArgumentException if the page is not in the buffer pool
   */
  public void flushPage(PageId pageno, FrameDesc frameinfo) {
	 
		if(frameinfo != null)
		{
			if (frameinfo.dirty == true) {
				
				Minibase.DiskManager.write_page(pageno, frameinfo);
			}
		}
		else
		{
			 throw new IllegalArgumentException("Page is not in the buffer pool!");
		}
	
  }

   /**
   * Gets the total number of buffer frames.
   */
  public int getNumFrames() {
    
	  return bufmap.size();

  }

  /**
   * Gets the total number of unpinned buffer frames.
   */
  public int getNumUnpinned() {

      int unpinned = 0;

      if (!bufferpool.isEmpty()) {
          unpinned += bufferpool.size();
      }
      Iterator map = bufmap.entrySet().iterator();
      while (map.hasNext()) {
          Map.Entry pair = (Map.Entry) map.next();
          PageId key = (PageId) pair.getKey();
          FrameDesc value = (FrameDesc) pair.getValue();
          if (value.pin_count == 0) {
              unpinned++;
          }
      }

      return unpinned;
  }

} 
