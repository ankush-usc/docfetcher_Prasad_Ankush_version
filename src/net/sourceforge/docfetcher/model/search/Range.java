/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model.search;

/**
 * @author Tran Nam Quang
 */
public final class Range {
	
	public final int start;
	public final int length;
	public final int r;
	public final int g;
	public final int b;
	Range(int start, int length,int r,int g,int b) {
		this.start = start;
		this.length = length;
		this.r = r;
		this.g = g;
		this.b = b;
	}
	

}
