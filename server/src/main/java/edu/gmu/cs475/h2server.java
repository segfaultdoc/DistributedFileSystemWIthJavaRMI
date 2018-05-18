package edu.gmu.cs475;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
//import java.util.concurrent.Runnable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import edu.gmu.cs475.internal.ServerMain;
import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.TagExistsException;


public class FileTagManagerServer implements IFileTagManager {

	private final ScheduledThreadPoolExecutor timerExecutorService = new ScheduledThreadPoolExecutor(2);
  // Map of Tag objects to their names
  private ConcurrentHashMap <String, Tag> tagMap = new ConcurrentHashMap <String, Tag>();
  // keeps track of all files
  private HashSet <TaggedFile> fileSet = new HashSet <TaggedFile> ();
  private ReadWriteLock lock = new ReentrantReadWriteLock();
  // these two data structures keep track of all readlocked and write
  // locked files
  private List <String> readLockedFiles = new ArrayList <String> ();
  private List <String> writeLockedFiles = new ArrayList <String> ();

	@Override
	public String readFile(String file) throws RemoteException, IOException {
		return new String(Files.readAllBytes(Paths.get(file)));
	}


	//TODO - implement all of the following methods:

	/**
	 * Initialize your FileTagManagerServer with files
	 * Each file should start off with the special "untagged" tag
	 * @param files
	 */
	public void init(List<Path> files) {
    // writelocks this object
    this.lock.writeLock().lock();
    try{
      // initializes a taggedfile
      TaggedFile taggedFile = null;
      // adds an untagged object mapped to "untagged"
      // only once to the map
      this.tagMap.put("untagged", new Tag("untagged"));
      // loops through the list of paths and initializes 
      // the TaggedFile objects. It then tags each object
      // with untagged.
      for(Path p: files){
        taggedFile = new TaggedFile(p);
        taggedFile.addTag("untagged");
        this.fileSet.add(taggedFile);
      }
    }
    finally{
      this.lock.writeLock().unlock();
    }
  
  }

	/**
	 * List all currently known tags.
	 *
	 * @return List of tags (in any order)
	 */
	@Override
	public Iterable<String> listTags() throws RemoteException {
		// sets a readlock on this object, since this method
    // performs no mutating operations on any resources
    this.lock.readLock().lock();
    // Iterates through the collection of tagObjects, and
    // adds each of their String-ed names to the ArrayList <String>
    // to return
    Collection <String> tagString = new ArrayList<String>();
    Collection <Tag> tags = null;
    try{
      tags = new ArrayList<Tag> (this.tagMap.values());
      for(Tag t: tags){
        tagString.add(t.getName());
      }
      return tagString;
    
    } finally{
        this.lock.readLock().unlock();
    }
    
	}

	/**
	 * Add a new tag to the list of known tags
	 *
	 * @param name Name of tag
	 * @return The newly created Tag name
	 * @throws TagExistsException If a tag already exists with this name
	 */
	@Override
	public String addTag(String name) throws RemoteException, TagExistsException {
		this.lock.writeLock().lock();
    if(name != null){
    try{
      
      if(!(this.tagDNE(name))){
        throw new TagExistsException();
      }
      else{
        Tag newTag = new Tag(name);
        this.tagMap.put(name, newTag);
        return name;

      }
    } finally{

        this.lock.writeLock().unlock();
      }
	}
  return name;
  }
  /**
   * Calls javas hashmap function to
   * check if the given tagname is contained
   * True if tag does not exist
   */
   public boolean tagDNE(String tagName){

      return !(this.tagMap.containsKey(tagName));
   }

	/**
	 * Update the name of a tag, also updating any references to that tag to
	 * point to the new one
	 *
	 * @param oldTagName Old name of tag
	 * @param newTagName New name of tag
	 * @return The newly updated Tag name
	 * @throws TagExistsException If a tag already exists with the newly requested name
	 * @throws NoSuchTagException If no tag exists with the old name
	 */
	@Override
	public String editTag(String oldTagName, String newTagName) throws RemoteException, TagExistsException, NoSuchTagException {
		this.lock.writeLock().lock();
    try{
      if(this.tagDNE(oldTagName)){
        throw new NoSuchTagException();
      }
      else if(!(this.tagDNE(newTagName))){
        throw new TagExistsException();
      }
      else{
        Tag oldTag = this.getTag(oldTagName);
        oldTag.setName(newTagName);
        return newTagName;

      }
    }
    finally{
      this.lock.writeLock().unlock();
    }
	}
  
  /**
   * @param tagName name of tag to return
   * @return tag specified by parameter
   *
   */
  public Tag getTag(String tagName){
    return this.tagMap.get(tagName);
  }

	/**
	 * Delete a tag by name
	 *
	 * @param tagName Name of tag to delete
	 * @return The tag name that was deleted
	 * @throws NoSuchTagException         If no tag exists with that name
	 * @throws DirectoryNotEmptyException If tag currently has files still associated with it
	 */
	@Override
	public String deleteTag(String tagName) throws RemoteException, NoSuchTagException, DirectoryNotEmptyException {
		this.lock.writeLock().lock();
    try{
      if(this.tagDNE(tagName)){
        throw new NoSuchTagException();
      }
      else if(this.isTagAssigned(tagName)){
        throw new DirectoryNotEmptyException("This tag is assigned to a file");
      }
      else{
        Tag tag = this.getTag(tagName);
        this.tagMap.remove(tagName);
        return tagName;

      }

    } finally{
        this.lock.writeLock().unlock();
    }

	}

  /**
   * Called when checking if directory empty/(is tag assigned to a file?)
   * @param tagName name of tag to check
   * @return true if tag is assigned (directory is not empty)
   *
   */
  public boolean isTagAssigned(String tagName){
    for(TaggedFile file: this.fileSet){
      if(file.hasTag(tagName)){
        return true;
      }

    }
    return false;
  }

  /**
   * Calls java's hashset contains() method
   * to check if given file exists
   * @param fileName name of file to check
   * @return true if file does not exist
   *
   */
   public boolean fileDNE(String fileName){
     for(TaggedFile f: this.fileSet){
        if(f.toString().equals(fileName)){ return false; }

     }
     return true;
     }

	/**
	 * List all files, regardless of their tag
	 *
	 * @return A list of all files. Each file must appear exactly once in this
	 * list.
	 */
	@Override
	public Iterable<String> listAllFiles() throws RemoteException {
		//synchronized(this.fileSet){
      this.lock.readLock().lock();
      try{
      ArrayList <String> f = new ArrayList<String> ();
      for(TaggedFile file: this.fileSet){
        synchronized(f){
          f.add(file.getName());
        }
      }
      return f;
      }
      finally{
        this.lock.readLock().unlock();
      }
      
   // }
	}

	/**
	 * List all files that have a given tag
	 *
	 * @param tag Tag to look for
	 * @return A list of all files that have been labeled with the specified tag
	 * @throws NoSuchTagException If no tag exists with that name
	 */
	@Override
	public Iterable<String> listFilesByTag(String tag) throws RemoteException, NoSuchTagException {
		//synchronized(this.fileSet){
    this.lock.readLock().lock();  
    try{
    if(this.tagDNE(tag) && !(tag.equals("untagged"))){
        throw new NoSuchTagException();
      }
      else{
        Set <String> taggedSet = new HashSet <String> ();
        TaggedFile tmp = null;
        for(TaggedFile file: this.fileSet){
          if(file.hasTag(tag)){
            taggedSet.add(file.getName());
          }
        }
        return taggedSet;
      }
    }
    finally{
      this.lock.readLock().unlock();
    }
	}

	/**
	 * Label a file with a tag
	 * <p>
	 * Files can have any number of tags - this tag will simply be appended to
	 * the collection of tags that the file has. However, files can be tagged
	 * with a given tag exactly once: repeatedly tagging a file with the same
	 * tag should return "false" on subsequent calls.
	 * <p>
	 * If the file currently has the special tag "untagged" then that tag should
	 * be removed - otherwise, this tag is appended to the collection of tags on
	 * this file.
	 *
	 * @param file Path to file to tag
	 * @param tag  The desired tag
	 * @throws NoSuchFileException If no file exists with the given name/path
	 * @throws NoSuchTagException  If no tag exists with the given name
	 * @returns true if succeeding tagging the file, false if the file already
	 * has that tag
	 */
	@Override
	public boolean tagFile(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException {
		this.lock.writeLock().lock();
    try{
      if(tag.equals("untagged")){ return false; }
      else if(this.tagDNE(tag)){
        throw new NoSuchTagException();
      }
      else if(this.fileDNE(file)){
        throw new NoSuchFileException("This file does not exist");
      }
      else{
        TaggedFile taggedFile = this.getFile(file);
        if(taggedFile.hasTag(tag)){ return false; }
        else {
          if(taggedFile.hasTag("untagged")){ taggedFile.removeTag("untagged"); }
          taggedFile.addTag(tag);
          return true;
        }
      }
    } finally{
        this.lock.writeLock().unlock();
      }
	}

    /**
    * @param fileName name of file to get
    * @return file or null
    *
    */
    public TaggedFile getFile(String fileName){
    
      for(TaggedFile f: this.fileSet){
        if(f.getName().equals(fileName)){ return f; }
      }
      return null;
    }

	/**
	 * Remove a tag from a file
	 * <p>
	 * If removing this tag causes the file to no longer have any tags, then the
	 * special "untagged" tag should be added.
	 * <p>
	 * The "untagged" tag can not be removed (return should be false)
	 *
	 * @param file Path to file to untag
	 * @param tag  The desired tag to remove from that file
	 * @throws NoSuchFileException If no file exists with the given name/path
	 * @throws NoSuchTagException  If no tag exists with the given name
	 * @returns True if the tag was successfully removed, false if there was no
	 * tag by that name on the specified file
	 */
	@Override
	public boolean removeTag(String file, String tag) throws RemoteException, NoSuchFileException, NoSuchTagException {
		//synchronized(this.tagMap){
    this.lock.writeLock().lock();
    try{
    if(tag.equals("untagged")){ return false; }
      else if(this.tagDNE(tag)){ throw new NoSuchTagException(); }
      else if(this.fileDNE(file)){ throw new NoSuchFileException("This file does not exist"); }
      TaggedFile f = this.getFile(file);
      if(f != null && f.hasTag(tag)){
        f.removeTag(tag);
        if(f.hasNoTags()){
          f.addTag("untagged");
        }
        return true;
      }
      else { return false; }
    }
    finally{
      this.lock.writeLock().unlock();
    }
	}

	/**
	 * List all of the tags that are applied to a file
	 *
	 * @param file The file to inspect
	 * @return A list of all tags that have been applied to that file in any
	 * order
	 * @throws NoSuchFileException If the file specified does not exist
	 */
	@Override
	public Iterable<String> getTags(String file) throws RemoteException, NoSuchFileException {
		//synchronized(this.fileSet){
    this.lock.readLock().lock();
    try{
    if(this.fileDNE(file)){ throw new NoSuchFileException("This file does not exist"); }
      else{
        ArrayList <String> t = new ArrayList <String> ();
        TaggedFile tf = this.getFile(file);
        for(Tag tag: tf.getTags()){
          t.add(tag.getName());
        }
        return t;
      }
    }
    finally{
      this.lock.readLock().unlock();
    }
	}

	/**
	 * Acquires a read or write lock for a given file.
	 *
	 * @param name     File to lock
	 * @param forWrite True if a write lock is requested, else false
	 * @return A stamp representing the lock owner (e.g. from a StampedLock)
	 * @throws NoSuchFileException If the file doesn't exist
	 */
	@Override
	public long lockFile(String name, boolean forWrite) throws RemoteException, NoSuchFileException {
		TaggedFile f = null;
    synchronized(this.fileSet){
      f = this.getFile(name);
      if(f == null){ throw new NoSuchFileException("This file does not exist"); }
    }
    long stamp;
    synchronized(f){   
    
    if(forWrite == true){  
      stamp = f.getLock().writeLock(); 
      f.setWriteLock(stamp);
      if(!this.writeLockedFiles.contains(name)){
        this.writeLockedFiles.add(name);
      }
    }
    else { 
      stamp = f.getLock().readLock(); 
      if(!this.readLockedFiles.contains(name)){
        this.readLockedFiles.add(name);
      }
      f.addReadLock(new Long(stamp));
    }
    
    f.schedule(stamp, timerExecutorService.scheduleAtFixedRate( new Runnable() {
                                                      @Override
                                                      public void run(){
                                                        try{
                                                          unLockFile(name, stamp, forWrite);
                                                        } catch(Exception e){
                                                            //e.printStackTrace();
                                                        }
                                                      }
                                                  }, 3, 3, TimeUnit.SECONDS) );

    }
    return stamp;
    
	}

	/**
	 * Releases a read or write lock for a given file.
	 *
	 * @param name     File to lock
	 * @param stamp    the Stamp representing the lock owner (returned from lockFile)
	 * @param forWrite True if a write lock is requested, else false
	 * @throws NoSuchFileException          If the file doesn't exist
	 * @throws IllegalMonitorStateException if the stamp specified is not (or is no longer) valid
	 */
	@Override
	public void unLockFile(String name, long stamp, boolean forWrite) throws RemoteException, NoSuchFileException, IllegalMonitorStateException {
    
    TaggedFile f = null;
    synchronized(this.fileSet){
      f = this.getFile(name);
      if(f == null){ throw new NoSuchFileException("This file does not exist"); }
    }
    
    this.lock.writeLock().lock();
   
    try{
     if(!f.getSchedule().containsKey(stamp)){
       throw new IllegalMonitorStateException();
    }
    else{
      
      
       if(forWrite == true){
        if(f.getWriteLock() != stamp || !this.writeLockedFiles.contains(name)){ 
          throw new IllegalMonitorStateException(); 
        }
         this.writeLockedFiles.remove(name);
         f.getLock().unlockWrite(stamp);
         f.setWriteLock(0);
         f.cancel(stamp);
       }
       else{
         if(!f.hasReadLock(stamp) || !this.readLockedFiles.contains(name)) { 
           throw new IllegalMonitorStateException(); 
         
         }
         //f.getLock().unlockRead(stamp);
          f.cancel(stamp);
         f.removeReadLock(stamp);
         if(f.getReadLocks().size() == 0){  
           this.readLockedFiles.remove(name);
         }
       }
     }
    }
    finally { 
              this.lock.writeLock().unlock();
    }

  }

	/**
	 * Notifies the server that the client is still alive and well, still using
	 * the lock specified by the stamp provided
	 *
	 * @param file    The filename (same exact name passed to lockFile) that we are
	 *                reporting in on
	 * @param stampId Stamp returned from lockFile that we are reporting in on
	 * @param isWrite if the heartbeat is for a write lock
	 * @throws IllegalMonitorStateException if the stamp specified is not (or is no longer) valid, or if
	 *                                      the stamp is not valid for the given read/write state
	 * @throws NoSuchFileException          if the file specified doesn't exist
	 */
	@Override
	public  void heartbeat(String file, long stampId, boolean isWrite) throws RemoteException, IllegalMonitorStateException, NoSuchFileException {
    TaggedFile f = null;
    
    synchronized(this.fileSet){
      f = this.getFile(file);
      if(f == null){ throw new NoSuchFileException("This file does not exist"); }
    }
    
    //synchronized(f){
    //this.lockFile(file, isWrite);
    if(isWrite == true){
      if(!this.getWriteLockedFiles().contains(file) || f.getWriteLock() != stampId){ 
        f.setWriteLock(0);
        throw new IllegalMonitorStateException(); 
      }
      else{
        f.cancel(stampId);
        f.schedule(stampId, timerExecutorService.scheduleAtFixedRate( new Runnable() {
                                                      @Override
                                                      public void run(){
                                                        try{
                                                          unLockFile(file, stampId, isWrite);
                                                        } catch(Exception e){
                                                            //e.printStackTrace();
                                                        }
                                                      }
                                                  }, 3, 3, TimeUnit.SECONDS)   );
      }
    }
    else{
      if(!this.getReadLockedFiles().contains(file)){ throw new IllegalMonitorStateException(); }
      else if(!f.hasReadLock(stampId)){ throw new IllegalMonitorStateException(); }
      else{
        //if(!f.getSchedule().containsKey(stampId)){
          f.cancel(stampId);
          f.schedule(stampId, timerExecutorService.scheduleAtFixedRate( new Runnable() {
                                                      @Override
                                                      public void run(){
                                                        try{
                                                          unLockFile(file, stampId, isWrite);
                                                        } catch(Exception e){
                                                            //e.printStackTrace();
                                                        }

                                                        }
                                                  }, 3, 3, TimeUnit.SECONDS)   );
      }
    }
   // }
   
  }
	/**
	 * Get a list of all of the files that are currently write locked
	 */
	@Override
	public List<String> getWriteLockedFiles() throws RemoteException {
		return new ArrayList <String> (this.writeLockedFiles);
	}

	/**
	 * Get a list of all of the files that are currently write locked
	 */
	@Override
	public List<String> getReadLockedFiles() throws RemoteException {
		return new ArrayList <String> (this.readLockedFiles);
	}

	@Override
	public void writeFile(String file, String content) throws RemoteException, IOException {
		Path path = Paths.get(file);
		if (!path.startsWith(ServerMain.BASEDIR))
			throw new IOException("Can only write to files in " + ServerMain.BASEDIR);
		Files.write(path, content.getBytes());

	}
}
