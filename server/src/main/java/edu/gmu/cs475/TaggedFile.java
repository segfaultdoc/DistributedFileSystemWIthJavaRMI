package edu.gmu.cs475;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.locks.StampedLock;
import java.util.concurrent.*;

import edu.gmu.cs475.struct.ITaggedFile;

public class TaggedFile implements ITaggedFile {
	public HashMap<String, Tag> tags = new HashMap<String, Tag>();
  private Path path;
	private StampedLock lock = new StampedLock();
  private ArrayList <Long> readLocks = new ArrayList <Long> ();
  private long writeLock = 0;
  private ConcurrentHashMap <Long, ScheduledFuture <?> > Schedules = new ConcurrentHashMap <Long, ScheduledFuture <?> > ();
  
  public void schedule(long key, ScheduledFuture <?> value){
    this.cancel(key);    
    this.Schedules.put(new Long (key), value);
  }

  public void cancel(long stamp){
    for(Long l: this.Schedules.keySet()){
      if(l.longValue() == stamp){
        this.Schedules.get(l).cancel(true);
        this.Schedules.remove(l);
        break;
      }
      
    }


  }
  
  public synchronized ConcurrentHashMap getSchedule(){
    return this.Schedules;
  }

  public synchronized void setWriteLock(long stamp){
    this.writeLock = stamp;
  }
  public synchronized long getWriteLock(){
    return this.writeLock;
  }
  public synchronized void addReadLock(Long lock){
    this.readLocks.add(lock);
  }
  public synchronized void removeReadLock(long lock){
   try{
    for(Long l: this.readLocks){
      if(l.longValue() == lock){ 
        this.readLocks.remove(l); 
      }
    }
   }
    catch(Exception e){ 
    }
    finally{
      //if(this.readLocks.size() == 0){
      this.getLock().unlockRead(lock);
     // }
    }
  }
  public synchronized ArrayList<Long> getReadLocks(){
    return new ArrayList <Long> (this.readLocks);
  }
  public synchronized boolean hasReadLock(long stamp){
    
    for(Long l: readLocks){
      if(l.longValue() == stamp){ return true; }
    }
    return false;

  }
  public StampedLock getLock(){
    return this.lock;
  }	
	
  public TaggedFile(Path path)
	{
		this.path = path;
	}
	@Override
	public String getName() {
		return path.toString();
	}
	@Override
	public String toString() {
		return getName();
	}

    public boolean addTag(String tagName){
        if(this.tags.containsKey(tagName)){
            return false;
        }
        else{
            this.tags.put(tagName, new Tag(tagName));
            return true;
        }
    }
    
    public Collection<Tag> getTags(){
        return this.tags.values();
    }

    public boolean hasTag(String tagName){
        return this.tags.containsKey(tagName);
    }
    
    public void removeTag(String tagName){
        long stamp = this.lock.writeLock();
        try{
            this.tags.remove(tagName);
        }
        finally { this.lock.unlockWrite(stamp); }

    }
    // returns true of no tags
    public boolean hasNoTags(){
        return this.tags.size()==0;
    }
}
