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
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.svg2svg.SVGTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;
import org.w3c.dom.svg.SVGSymbolElement;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 6/8/2015
 */
public final class Main {
	public static void main(String[] args) throws Throwable {
		OptionParser parser = new OptionParser();
		ArgumentAcceptingOptionSpec<Path> mappingOpt = parser.accepts("mapping").withRequiredArg().withValuesConvertedBy(new PathValueConverter()).required();
		ArgumentAcceptingOptionSpec<Path> imageOpt = parser.accepts("image").withRequiredArg().withValuesConvertedBy(new PathValueConverter()).required();
		ArgumentAcceptingOptionSpec<Path> outputOpt = parser.accepts("output").withRequiredArg().withValuesConvertedBy(new PathValueConverter()).required();
		OptionSet options = parser.parse(args);
		Path mappingPath = options.valueOf(mappingOpt);
		Path imagePath = options.valueOf(imageOpt);
		Path outputPath = options.valueOf(outputOpt);

		SVGDocument doc = (SVGDocument)SVGDOMImplementation.getDOMImplementation()
				.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, "svg", null);
		Map<Character, SVGSymbolElement> symbols = parseMapping(mappingPath, doc);

		int tileHeight = 50, tileWidth = 50;
		int maxLineLength = 0;
		List<String> imageLines = Files.readAllLines(imagePath);
		for (int r = 0; r < imageLines.size(); ++r) {
			String line = imageLines.get(r);
			maxLineLength = Math.max(maxLineLength, line.length());
			for (int c = 0; c < line.length(); ++c) {
				char t = line.charAt(c);
				SVGSymbolElement symbol = symbols.get(t);
				if (symbol == null) {
					System.err.println("no mapping for symbol: "+t);
					continue;
				}

				Element instance = doc.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "use");
				instance.setAttributeNS("http://www.w3.org/1999/xlink", "href", "#"+symbol.getId());
				instance.setAttributeNS(null, "x", Integer.toString(c*tileWidth));
				instance.setAttributeNS(null, "y", Integer.toString(r*tileHeight));
				instance.setAttributeNS(null, "height", Integer.toString(tileHeight));
				instance.setAttributeNS(null, "width", Integer.toString(tileWidth));
				doc.getDocumentElement().appendChild(instance);
			}
		}
		doc.getRootElement().setAttributeNS(null, "viewBox", String.format("0 0 %s %s", maxLineLength*tileWidth, imageLines.size()*tileHeight));
		doc.getRootElement().setAttributeNS(null, "preserveAspectRatio", "xMinYMin meet");

		SVGTranscoder transcoder = new SVGTranscoder();
		try (Writer w = Files.newBufferedWriter(outputPath)) {
			transcoder.transcode(new TranscoderInput(doc), new TranscoderOutput(w));
			w.flush();
		}
	}

	private static Map<Character, SVGSymbolElement> parseMapping(Path path, SVGDocument doc) throws IOException {
		Map<Character, SVGSymbolElement> symbols = new HashMap<>();
		int counter = 0;
		for (String line : Files.readAllLines(path)) {
			int fieldSep = line.indexOf(' ');
			String symbol = line.substring(0, fieldSep);
			if (symbol.length() != 1)
				throw new RuntimeException("bad symbol: "+symbol);
			String elementStr = line.substring(fieldSep+1).trim();

			SVGSVGElement subdoc;
			if (elementStr.startsWith("@")) {
				Path svgDocPath = Paths.get(elementStr.substring(1));
				svgDocPath = path.resolveSibling(svgDocPath);
				try (Reader r = Files.newBufferedReader(svgDocPath)) {
					subdoc = parseRoot(r).getRootElement();
				}
			} else {
				try {
					subdoc = parseRoot(new StringReader(elementStr)).getRootElement();
				} catch (IOException ex) {
					//if we're just missing the outer svg element, add one and try again
					String message = ex.getMessage();
					if (message.contains("Root element namespace") && message.contains("Found: null")) {
						elementStr = "<svg xmlns=\"http://www.w3.org/2000/svg\">" + elementStr + "</svg>";
						subdoc = parseRoot(new StringReader(elementStr)).getRootElement();
					} else
						throw ex;
				}
				if (subdoc == null) {
					System.out.println("problem parsing element for "+symbol);
					continue;
				}
			}
			SVGSymbolElement sym = (SVGSymbolElement)doc.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "symbol");
			while (subdoc.hasChildNodes())
				sym.appendChild(doc.adoptNode(subdoc.getFirstChild()));
			String viewBox = subdoc.getAttribute("viewBox"), width = subdoc.getAttribute("width"),
					height = subdoc.getAttribute("height");
			if (!viewBox.isEmpty())
				sym.setAttributeNS(null, "viewBox", viewBox);
			else if (!width.isEmpty() && !height.isEmpty()) {
				sym.setAttributeNS(null, "viewBox", String.format("0 0 %s %s", width, height));
				System.out.println("element for "+symbol+" has no viewBox; assuming height and width are accurate");
			} else
				System.out.println("element for "+symbol+" has no size information");
			sym.setId("svgtilersymbol"+(counter++));
			doc.getDocumentElement().appendChild(sym);
			symbols.put(symbol.charAt(0), sym);
		}
		return symbols;
	}

	/**
	 * Parses an SVG document from the given string.
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	private static SVGDocument parseRoot(Reader r) throws IOException {
		String parser = XMLResourceDescriptor.getXMLParserClassName();
		SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
		SVGDocument doc = f.createSVGDocument("", r);
		return doc;
	}

	private static final class PathValueConverter implements ValueConverter<Path> {
		@Override
		public Path convert(String value) {
			try {
				return Paths.get(value);
			} catch (InvalidPathException ex) {
				throw new ValueConversionException("", ex);
			}
		}
		@Override
		public Class<Path> valueType() {
			return Path.class;
		}
		@Override
		public String valuePattern() {
			return null;
		}
	}
}
