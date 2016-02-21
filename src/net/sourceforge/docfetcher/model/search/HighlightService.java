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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.index.IndexWriterAdapter;
import net.sourceforge.docfetcher.util.CheckedOutOfMemoryError;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.MutableCopy;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.VisibleForPackageGroup;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.NullFragmenter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.TokenGroup;
import org.apache.lucene.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene.search.vectorhighlight.FieldPhraseList;
import org.apache.lucene.search.vectorhighlight.FieldPhraseList.WeightedPhraseInfo;
import org.apache.lucene.search.vectorhighlight.FieldQuery;
import org.apache.lucene.search.vectorhighlight.FieldTermStack;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.eclipse.swt.graphics.Color;

import com.google.common.io.Closeables;

/**
 * @author Tran Nam Quang
 */
@VisibleForPackageGroup
public final class HighlightService {
	public static Map<String,int[]> key_color_mapper = new HashMap<String,int[]>();

	private HighlightService() {
	}
	
	// The given text is trimmed
	@NotNull
	public static HighlightedString highlight(	@NotNull Query query,
												boolean isPhraseQuery,
												@NotNull String text)
			throws CheckedOutOfMemoryError {
		text = trimDocument(text);
		List<Range> ranges;
		/* Construct color hash */
		
		String keystring = query.toString("content");
		String keys[] = keystring.split(" ");
		for(String key:keys){
			int[] a = new int[3];
			Random rn = new Random();
			a[0]= rn.nextInt(200);
			a[1]= rn.nextInt(200);
			a[2] = rn.nextInt(200);
 			key_color_mapper.put(key, a);
		}
		if (isPhraseQuery)
			ranges = highlightPhrases(query, text);
		else
			ranges = highlight(query, text);
		return new HighlightedString(text, ranges);
	}
	
	/**
	 * Trims the given string as follows:
	 * <ul>
	 * <li>All trailing whitespace is removed.</li>
	 * <li>All preceding empty lines are removed. This means that any leading
	 * whitespace in the first non-empty line is preserved.</li>
	 * </ul>
	 */
	@NotNull
	private static String trimDocument(@NotNull String input) {
		input = Util.trimRight(input);
		Integer lineStart = 0;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '\r' || c == '\n') {
				lineStart = i + 1;
			} else if (!Character.isWhitespace(c)) {
				if (lineStart != null)
					return input.substring(lineStart);
				return input.substring(i);
			}
		}
		return "";
	}
	
	@MutableCopy
	@NotNull
	@SuppressWarnings("unchecked")
	private static List<Range> highlightPhrases(@NotNull Query query,
												@NotNull String text)
			throws CheckedOutOfMemoryError {
		// FastVectorHighlighter only supports TermQuery, PhraseQuery and BooleanQuery
		FastVectorHighlighter highlighter = new FastVectorHighlighter(true, true, null, null);
		FieldQuery fieldQuery = highlighter.getFieldQuery(query);
		Directory directory = new RAMDirectory();
		try {
			/*
			 * Hack: We have to put the given text in a RAM index, because the
			 * fast-vector highlighter can only work on index readers
			 */
			IndexWriterAdapter writer = new IndexWriterAdapter(directory);
			Document doc = new Document();
			doc.add(Fields.createContent(text, true)); // must store token positions and offsets
			writer.add(doc);
			Closeables.closeQuietly(writer); // flush unwritten documents into index
			IndexReader indexReader = IndexReader.open(directory);
			
			// This might throw an OutOfMemoryError
			FieldTermStack fieldTermStack = new FieldTermStack(
				indexReader, 0, Fields.CONTENT.key(), fieldQuery);
			
			FieldPhraseList fieldPhraseList = new FieldPhraseList(fieldTermStack, fieldQuery);
			
			// Hack: We'll use reflection to access a private field
			java.lang.reflect.Field field = fieldPhraseList.getClass().getDeclaredField("phraseList");
			field.setAccessible(true);
			LinkedList<WeightedPhraseInfo> infoList = (LinkedList<WeightedPhraseInfo>) field.get(fieldPhraseList);
			
			List<Range> ranges = new ArrayList<Range> (infoList.size());
			for (WeightedPhraseInfo phraseInfo : infoList) {
				/* AHP: find this weightedphrase */
				/* Phrase info has string-range */
				/* Compare your hash and insert right color here */
				String string_to_hunt = phraseInfo.toString();
				int c1=0;
				int c2=0;
				int c3=0;
				for(String keys: key_color_mapper.keySet()){
					int[] temp = key_color_mapper.get(keys);
					keys = keys + "(";
					if(string_to_hunt.contains(keys)){
						c1 = temp[0];
						c2 = temp[1];
						c3 = temp[2];
						break;
					}
				}
				System.out.println("HighlightService_ahp: colors c1 = "+c1+" , c2 = "+c2+ " c3 = "+c3);	
				int start = phraseInfo.getStartOffset();
				int end = phraseInfo.getEndOffset();
				ranges.add(new Range(start, end - start,c1,c2,c3));
			}
			return ranges;
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		catch (Exception e) {
			return new ArrayList<Range> (0);
		}
	}
	
	@MutableCopy
	@NotNull
	private static List<Range> highlight(	@NotNull Query query,
											@NotNull String text)
			throws CheckedOutOfMemoryError {
		final List<Range> ranges = new ArrayList<Range> ();
		/*
		 * A formatter is supposed to return formatted text, but since we're
		 * only interested in the start and end offsets of the search terms, we
		 * return null and store the offsets in a list.
		 */
		Formatter nullFormatter = new Formatter() {
			public String highlightTerm(String originalText, TokenGroup tokenGroup) {
				for (int i = 0; i < tokenGroup.getNumTokens(); i++) {
					Token token = tokenGroup.getToken(i);
					if (tokenGroup.getScore(i) == 0)
						continue;
					/* AHP: find what is our token */
					int c1=0;
					int c2=0;
					int c3=0;
					for(String keys: key_color_mapper.keySet()){
						int[] temp = key_color_mapper.get(keys);
						if((token.toString()).contains(keys)){
							c1 = temp[0];
							c2 = temp[1];
							c3 = temp[2];
							break;
						}
					}
					System.out.println("HighlightService_ahp: Token ="+token.toString());
					int start = token.startOffset();
					int end = token.endOffset();
					ranges.add(new Range(start, end - start,c1,c2,c3));
				}
				return null;
			}
		};
		/* AHP investigate here on changing text colors*/
		String key = Fields.CONTENT.key();
		Highlighter highlighter = new Highlighter(nullFormatter, new QueryScorer(query, key));
		highlighter.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);
		highlighter.setTextFragmenter(new NullFragmenter());
		try {
			/*
			 * This has a return value, but we ignore it since we only want the
			 * offsets. Might throw an OutOfMemoryError.
			 */
			 highlighter.getBestFragment(IndexRegistry.getAnalyzer(), key, text);
			//highlighter.getBestFragment(new StandardAnalyzer(Version.LUCENE_30), key,text);
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		catch (Exception e) {
			Util.printErr(e);
		}
		return ranges;
	}
	
}
