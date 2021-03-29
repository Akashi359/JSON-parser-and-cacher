package com.icon.json;

/*
	Parses a JSONObject file for any subset of the file and caches the results.
	
	Relies on JSONStreamer.java, which in turn relies on a specific implemenation of JSONTokener
	to function correctly (that is, it relies on duping the JSONTokener by passing in a BufferedReader that
	has been partially read, in order to retrieve subsections of the file).
	This file will likely break if the implemenations of JSONTokener or JSONObject change in the future.
	
	Currently lacks the ability to empty its cache - whatever gets retrieved will stay there until the
	program restarts.
*/
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.lang.StringBuilder;
import java.util.HashMap;
import java.util.Map;


public class JSONCacher{
	private static class Node{
		long position;
		boolean isLeaf;
		Object value;
		Node(long position, boolean isLeaf, Object value){
			this.position = position;
			this.isLeaf = isLeaf;
			this.value = value;
		}
	}
	
	private Node root;
	private String filePath;
	
	public JSONCacher(String filePath){
		root = new Node(0, false, null);
		this.filePath = filePath;
	}
	
	public Object getObject(String address) throws JSONException, FileNotFoundException, IOException{
		String[] lines = address.split("\\/");
		StringBuilder currAddr = new StringBuilder();
		Node currNode = this.root;
		for(int offset = 0; offset <= lines.length; offset++){
			//The target or its parent is cached in the tree in JSON format.
			if(currNode.isLeaf)
				return getFromLeaf(lines, offset, currNode, currAddr);
			//Either the target is cached in the tree but not in JSON format, or the target is not cached in the tree at all.
			if(offset >= lines.length || currNode.value == null)
				return populate(lines, offset, currNode, currAddr);
			//The target does not exist.
			Map<String,Node> currValue = (Map<String,Node>) currNode.value;
			if(!currValue.containsKey(lines[offset]))
				throw new JSONException("Key '" + lines[offset] + "' not found in object " + currAddr.toString() + ".");
			//The target's ancestor is cached in the tree in non-JSON format
			currAddr.append(lines[offset]+"\\");
			currNode = currValue.get(lines[offset]);
		}
		throw new JSONException("Exited for loop in getObject() - shouldn't be possible to get here");
	}
	
	private Object getFromLeaf(String[] lines, int offset, Node currNode, StringBuilder currAddr) throws JSONException{
		Object currValue = currNode.value;
		
		//The current node is not the target.
		for(int k = offset; k < lines.length; k++){
			//Path treats non-JSONObject as JSONObject
			if(!(currValue instanceof JSONObject))
				throw new JSONException("Object '" + currAddr.toString() + "' is not a JSONObject.");
			JSONObject currJSON = (JSONObject) currValue;
			//The target does not exist.
			if(!currJSON.has(lines[k]))
				throw new JSONException("Key '" + lines[offset] + "' not found in object " + currAddr.toString() + ".");
			currAddr.append(lines[offset]+"\\");
			currValue = currJSON.get(lines[offset]);
		}
		
		//The current node is the target.
		return currValue;
	}
	
	private Object populate(String[] lines, int offset, Node currNode, StringBuilder currAddr) throws JSONException, FileNotFoundException, IOException{
		BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath));
		StringBuilder errorBuilder = new StringBuilder();
		Object retVal;
		try{
			JSONStreamer js = new JSONStreamer(currNode.position, bufferedReader);
			retVal = populateHelper(lines, offset, currNode, currAddr, js, errorBuilder);
		} finally {
			bufferedReader.close();
		}
		if(!errorBuilder.toString().equals("")){
			throw new JSONException(errorBuilder.toString());
		}
		return retVal;
	}
	
	private Object populateHelper(String[] lines, int offset, Node currNode, StringBuilder currAddr, JSONStreamer js, StringBuilder errorBuilder) throws JSONException, FileNotFoundException, IOException{
		//The current position in the bufferedReader is not the target
		if(offset < lines.length){
			//Check whether the next object is a JSONObject
			if(js.peekClean() != '{') {
				errorBuilder.append("Object '" + currAddr.toString() + "' is not a JSONObject.");
				return null;
			}
			
			//Search the JSONObject and cache all the keys found.
			Object retVal = null;
			boolean found = false;
			Map<String, Node> jsonAsMap = new HashMap<String,Node>();
			for(String currKey = js.getKey(); currKey != null; currKey = js.getKey()){
				Node newNode = new Node(js.getPosition(), false, null);
				jsonAsMap.put(currKey, newNode);
				if(currKey.equals(lines[offset])){
					found = true;
					retVal = populateHelper(lines, offset+1, newNode, currAddr.append(lines[offset]+"\\"), js, errorBuilder);
					int startIndex = currAddr.lastIndexOf(lines[offset]);
					currAddr.delete(startIndex, startIndex+lines[offset].length()+1);//+1 is because the '\' char isn't part of lines[offset]
				}
				js.skipValue();
			}
			currNode.value = jsonAsMap;
			if(!found)
				errorBuilder.append("Key '" + lines[offset] + "' was not found in object " + currAddr.toString());
			return retVal;
		}
		
		//The current position in the bufferedReader is the target
		currNode.isLeaf = true;
		//Create a copy of the bufferedReader for use by the JSONTokener
		//The JSONCacher needs to track the position of the BufferedReader, which the JSONTokener does not.
		BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath));
		bufferedReader.skip(js.getPosition());
		JSONTokener jsonTokener = new JSONTokener(bufferedReader);
		currNode.value = jsonTokener.nextValue();
		return currNode.value;
	}
	public String getTree(){
		StringBuilder sb = new StringBuilder();
		getLine(0,"{",sb);
		getNode(0, root, sb);
		getLine(0,"}",sb);
		return sb.toString();
	}
	private void getNode(int level, Node node, StringBuilder sb){
		getLine(level, " position: " + node.position + ",", sb);
		if(node.isLeaf)
			getLine(level, " isLeaf: true,", sb);
		else
			getLine(level, " isLeaf: false,", sb);
		if(node.value == null)
			getLine(level, " value: null", sb);
		else if(node.isLeaf)
			getLine(level, " value: " + node.value.toString(), sb);
		else{
			getLine(level, " value: {", sb);
			Map<String,Node> nodeValueAsMap = (Map<String,Node>) node.value;
			for(String key : nodeValueAsMap.keySet()){
				getLine(level+1, key + ":{", sb);
				getNode(level+1, nodeValueAsMap.get(key),sb);
				getLine(level+1, "},",sb);
			}
			getLine(level, " },",sb);
		}
	}
	private void getLine(int level, String line, StringBuilder sb){
		for(int k = 0; k < level; k++)
			sb.append('\t');
		sb.append(line);
		sb.append('\n');
	}
}
