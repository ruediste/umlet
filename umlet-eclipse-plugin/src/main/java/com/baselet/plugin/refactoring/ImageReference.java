package com.baselet.plugin.refactoring;

import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagAttr;
import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagStartNode;

public class ImageReference {

	public final HtmlTagStartNode tag;
	public final HtmlTagAttr srcAttr;

	public ImageReference(HtmlTagStartNode tag, HtmlTagAttr srcAttr) {
		this.tag = tag;
		this.srcAttr = srcAttr;
	}
}
