package com.deathrecap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ShareCardLayout
{
	WIDE("Wide", "Screenshot with the recap in a strip below"),
	SIDE("Side panel", "Screenshot with the recap in a column beside it"),
	TALL("Tall", "Screenshot on top with a large graph and the recap stacked below, for phones"),
	COMPACT("Recap only", "The recap and graph without the screenshot");

	private final String label;
	private final String description;

	@Override
	public String toString()
	{
		return label;
	}
}
