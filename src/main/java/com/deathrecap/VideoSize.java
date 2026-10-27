package com.deathrecap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VideoSize
{
	FULL("Full size", 0),
	HD("1280 wide", 1280),
	SMALL("854 wide", 854);

	private final String label;
	/** Widest frame allowed, 0 for the recorded size. */
	private final int maxWidth;

	@Override
	public String toString()
	{
		return label;
	}
}
