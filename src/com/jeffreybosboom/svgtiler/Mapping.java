/*
 * Copyright 2015 Jeffrey Bosboom
 * This file is part of svg-tiler.
 *
 * svg-tiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * svg-tiler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with svg-tiler. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jeffreybosboom.svgtiler;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 6/8/2015
 */
public final class Mapping implements Function<Character, SVGSVGElement> {
	private final Map<Character, SVGSVGElement> elements;
	public Mapping(Map<Character, SVGSVGElement> elements) {
		this.elements = elements;
	}

	public static Mapping fromFile(Path path) throws IOException {
		Map<Character, SVGSVGElement> map = new HashMap<>();
		for (String line : Files.readAllLines(path)) {
			int fieldSep = line.indexOf(' ');
			String symbol = line.substring(0, fieldSep);
			if (symbol.length() != 1)
				throw new RuntimeException("bad symbol: "+symbol);
			String elementStr = line.substring(fieldSep+1).trim();
			SVGSVGElement element;
			if (elementStr.startsWith("@")) {
				Path svgDocPath = Paths.get(elementStr.substring(1));
				svgDocPath = path.resolveSibling(svgDocPath);
				try (Reader r = Files.newBufferedReader(svgDocPath)) {
					element = parseRoot(r);
				}
			} else {
				try {
					element = parseRoot(new StringReader(elementStr));
				} catch (IOException ex) {
					//if we're just missing the outer svg element, add one and try again
					String message = ex.getMessage();
					if (message.contains("Root element namespace") && message.contains("Found: null")) {
						elementStr = "<svg xmlns=\"http://www.w3.org/2000/svg\">" + elementStr + "</svg>";
						element = parseRoot(new StringReader(elementStr));
					} else
						throw ex;
				}
			}
			map.put(symbol.charAt(0), element);
		}
		return new Mapping(map);
	}

	/**
	 * Parses an SVG document from the given string and returns the root svg
	 * element.
	 *
	 * TODO: auto-add outer svg element/namespace declaration for brevity?
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	private static SVGSVGElement parseRoot(Reader r) throws IOException {
		String parser = XMLResourceDescriptor.getXMLParserClassName();
		SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
		SVGDocument doc = f.createSVGDocument("", r);
		return doc.getRootElement();
	}

	@Override
	public SVGSVGElement apply(Character c) {
		SVGSVGElement e = elements.get(c);
		if (e != null)
			e = (SVGSVGElement)e.cloneNode(true);
		return e;
	}
}
