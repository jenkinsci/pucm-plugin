package net.praqma.scm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import hudson.model.User;
import hudson.scm.ChangeLogSet.Entry;

import net.praqma.debug.Debug;

/**
 * This class represents an entry in a changeset (TODO is class-description correct?)
 * A changeset is a collection of changed entries. This classe represents one entry, which is a user, a comment and a list of versions
 * @author Troels Selch S�rensen
 * @author Margit Bennetzen
 *
 */
public class ChangeLogEntryImpl extends Entry {
	
	private ChangeLogSetImpl parent; 
	private String msg;
	//TODO implement user 
	protected static Debug logger = Debug.GetLogger();
	private volatile List<String> affectedPaths = new ArrayList<String>(); //list of changed files
	
	public ChangeLogEntryImpl(){
		logger.trace_function();
	}

	/**
	 * Hudson calls this to show changes on the changes-page	
	 */
	@Override
	public Collection<String> getAffectedPaths() {
		logger.trace_function();
		//a baseline can be set without any files changed - but then we wont build
		return affectedPaths;
	}
	
	/**
	 *This method us used by ChangeLogParserImpl.parse to write the changelog
	 * @param filepath
	 */
	public void setNextFilepath(String filepath){
		logger.trace_function();
		if(filepath == null)
			logger.log("Filepath er null");
		affectedPaths.add(filepath);
	}
	
	/**
	 * Called by Hudson. This delivers the user that made the changesetentry
	 */
	@Override
	public User getAuthor() {
		// TODO Implement the right user when its ready in CC-code
		logger.trace_function();
		return User.getUnknown();
	}

	/**
	 * This is to tell the Entry which Changeset it belongs to 
	 * @param parent
	 */
	public void setParent(ChangeLogSetImpl parent){
		logger.trace_function();
		this.parent = parent;
	}

	/**
	 * Used in digest.jelly to get the message attached to the entry 
	 */
	@Override
	public String getMsg() {
		return msg;
	}
	
	/*
	 * TODO The Digester in ChangeLogParserImpl.parse() will use this when comment gets implemented 
	 * public void setMsg(String msg) {
		this.msg = msg;
	}*/
}