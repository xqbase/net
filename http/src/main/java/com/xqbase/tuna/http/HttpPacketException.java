package com.xqbase.tuna.http;

import java.io.IOException;

public class HttpPacketException extends IOException {
	private static final long serialVersionUID = 1L;

	public static final String
			HEADER_SIZE = "Header Size Exceeds Limit",
			START_LINE = "Invalid Start Line",
			VERSION = "Unrecognized Version",
			STATUS = "Invalid Status",
			CONTENT_LENGTH = "Invalid Content-Length",
			CHUNK_SIZE = "Invalid Chunk Size";

	private String type, value;

	public HttpPacketException(String type, String value) {
		super(type + ": \"" + value + "\"");
		this.type = type;
		this.value = value;
	}

	public String getType() {
		return type;
	}

	public String getValue() {
		return value;
	}
}