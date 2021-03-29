package com.icon.json;
/*
	An approximation of JSONTokener that exposes the internal BufferedReader,
	tracks and exposes the BufferedReader's progress through the file,
	and provides functions skipping through the file
	(that is, parsing the file without allocating space to return what it read).

	Currently incapable of parsing through the file and returning values that aren't keys.
	
	Relies on the implementation of JSONTokener to do so - this will break if the implementation
	of JSONTokener changes in the future.
*/
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Stack;

public class JSONStreamer{
    private BufferedReader reader;
    private boolean usePrevious;
    private char    previous;
    private boolean eof;
	private long position;
	
	JSONStreamer(long position, BufferedReader reader) throws IOException, IllegalArgumentException{
		usePrevious = false;
		this.reader = reader;
		eof = false;
		reader.skip(position);
		this.position = position;
	}
	
	/*
		Reads the next character and returns it.
		Advances the file pointer.
		Will return whitespace.
		
		Note: calling peekclean and then readRaw
		is functionally identical to readClean
		and will advance the filepointer anyways.
	*/
	public char readRaw() throws JSONException{
		if(usePrevious){
			usePrevious = false;
			return previous;
		}
		int i;
		try {
			i = this.reader.read();
			position++;
		} catch (IOException exception) {
			throw new JSONException(exception);
		}
		if (i < 0) { // End of stream
			this.eof = true;
			return 0;
		}
		return (char) i;
	}
	
	/*
		Reads the next character and returns it.
		Does not advance the file pointer.
		Will return whitespace.
		
		Note: calling peekClean and then peekRaw
		is functionally identical to peekClean twice.
		The file pointer will have been advanced past whitespace characters
		by the first call to peekClean.
	*/
	public char peekRaw() throws JSONException{
		if(usePrevious)
			return previous;
		previous = readRaw();
		usePrevious = true;
		return previous;
	}
	
	/*
		Returns the next non whitespace character
		and advances the file pointer.
	*/
	public char readClean() throws JSONException{
        for (;;) {
            char c = this.readRaw();
            if (c == 0 || !Character.isWhitespace(c)) {
                return c;
            }
        }
	}
	
	/*
		Returns the next non-ws character.
		Advances the file pointer only if the next character is whitespace.
	*/
	public char peekClean() throws JSONException{
		if(usePrevious && (previous == 0 || !Character.isWhitespace(previous)))
			return previous;
		previous = readClean();
		usePrevious = true;
		return previous;
	}
	
	/*
		Returns the index of the next character in the file.
	*/
	public long getPosition(){
		if(usePrevious)
			return position - 1;
		return position;
	}
	
	public BufferedReader getReader(){
		return this.reader;
	}
	
	public boolean isObject() throws JSONException{
		return peekClean() == '{';
	}
	
	public String getKey() throws JSONException{
		//consume first '{' to enter the JSONObject, if we haven't already
		if(peekClean() == '{')
			readClean();
		char firstChar = peekClean();
		String retVal;
		switch(firstChar){
			case '}':
				readClean();
				return null;
			case 0:
				throw new JSONException("Reached EoF before closing JSONObject");
			case '"':
			case '\'':
				retVal = getQuotedString();
				break;
			case ':':
			case ',':
			case ';':
			case '[':
			case ']':
			case '{':
				throw new JSONException("unexpected formatting character '" + firstChar  + "'. Expected String");
			default:
				retVal = getUnquotedString();
				break;
		}
		if(readClean() != ':')
			throw new JSONException("Expected ':' after key");
		return retVal;
	}
	
	public void skipValue() throws JSONException{
		char nextChar = peekClean();
		switch(nextChar){
			case 0:
				throw new JSONException("Expected value but received EoF instead");
			case '{':
				skipObject();
				break;
			case '[':
				skipArray();
				break;
			case '\'':
			case '\"':
				skipQuotedString(nextChar);
				break;
			default:
				skipUnquotedString();
				break;
		}
		nextChar = peekClean();
		switch(nextChar){
			case '}':
			case ']':
				return;
			case ',':
				readClean();
				return;
			default:
				throw new JSONException("Expected ',', '}', or ']' after value");
		}
	}
	
	public void skipObject() throws JSONException{
		//consume first '{' to enter the JSONObject
		char nextChar = readClean();
		if(nextChar != '{')
			throw new JSONException("Expected '{' but received '" + nextChar + "' instead");
		for(;;){
			nextChar = peekClean();
			switch(nextChar){
				case 0:
					throw new JSONException("Expected Key but received EoF instead");
				case '}':
					readClean();
					return;
				case ':':
				case ',':
				case ';':
				case '[':
				case ']':
				case '{':
					throw new JSONException("unexpected formatting character '" + nextChar  + "'. Expected Key");
				case '\'':
				case '\"':
					skipQuotedString(nextChar);
					break;
				default:
					skipUnquotedString();
					break;
			}
			if(readClean() != ':')
				throw new JSONException("Expected ':' after key");
			skipValue();
		}
	}
	
	public void skipArray() throws JSONException{
		//consume first '[' to enter the JSONArray
		char nextChar = readClean();
		if(nextChar != '[')
			throw new JSONException("Expected '[' but received '" + nextChar + "' instead");
		for(;;){
			nextChar = peekClean();
			switch(nextChar){
				case 0:
					throw new JSONException("Expected Value but received EoF instead");
				case ']':
					readClean();
					return;
				default:
					skipValue();
			}
		}
	}
	
	public void skipQuotedString(char type){
		char nextChar = readClean();
		if(nextChar != type)
			throw new JSONException("Expected '" + type + "' at the start of the next Quoted String");
		for(;;){
			nextChar = readClean();
			if(nextChar == 0)
				throw new JSONException("Reached EoF before closing Quoted String");
			else if(nextChar == '\\')
				readClean();	//ignore next character
			else if(nextChar == type)
				return;
		}
	}
	
	public void skipUnquotedString(){
		for(;;){
			//check the next character and if its a formatting character, we are out of the String.
			char nextChar = peekRaw();
			if(Character.isWhitespace(nextChar))
				return;
			switch(nextChar){
				case 0:
					throw new JSONException("Reached EoF before closing String");
				case ']':
				case '}':
				case ':':
				case ',':
				case ';':
					return;
				case '"':
				case '\'':
				case '[':
				case '{':
				case '\\':
					throw new JSONException("unexpected formatting character '" + nextChar + "' in unquoted String");
			}
			
			//if its not a formatting character, consume and continue
			readRaw();
		}
	}
	
	public String getQuotedString() throws JSONException{
		char quoteType = readClean();
		StringBuilder sb = new StringBuilder();
		for(;;){
			char nextChar = readRaw();
			switch(nextChar){
				case 0:
					throw new JSONException("Reached EoF before closing String");
				case '\\':
					nextChar = readRaw();
					
					switch(nextChar){
						case 0:
							throw new JSONException("Reached EoF before closing String");
						case 'b':
							sb.append('\b');
							break;
						case 't':
							sb.append('\t');
							break;
						case 'n':
							sb.append('\n');
							break;
						case 'f':
							sb.append('\f');
							break;
						case 'r':
							sb.append('\r');
							break;
						case '"':
						case '\'':
						case '\\':
						case '/':
							sb.append(nextChar);
							break;
						default:
							throw new JSONException("Unknown escaped character '\\"+nextChar+"'");
					}
					break;
				default:
					if(nextChar == quoteType)
						return sb.toString();
					sb.append(nextChar);
			}
		}
	}
	
	public String getUnquotedString() throws JSONException{
		StringBuilder sb = new StringBuilder();
		for(;;){
			//check the next character and if its a formatting character, process without consuming.
			char nextChar = peekRaw();
			if(Character.isWhitespace(nextChar))
				return sb.toString();
			switch(nextChar){
				case 0:
					throw new JSONException("Reached EoF before closing String");
				case ']':
				case '}':
				case ':':
				case ',':
				case ';':
					return sb.toString();
				case '"':
				case '\'':
				case '[':
				case '{':
				case '\\':
					throw new JSONException("unexpected formatting character '" + nextChar + "' in unquoted String");
			}
			
			//if its not a formatting character, consume and append
			readRaw();
			sb.append(nextChar);
		}
	}
	
}
