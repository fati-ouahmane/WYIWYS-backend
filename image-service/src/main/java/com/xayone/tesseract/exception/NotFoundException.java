package com.xayone.tesseract.exception;


public class NotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	
	private Integer status;

	public NotFoundException() {
		super();
	}

	public NotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public NotFoundException(String message) {
		super(message);
	}

	public NotFoundException(String message, int status) {
		super(message);
		this.status = status;
	}

	public Integer getStatus() {
		return status;
	}
	
}
