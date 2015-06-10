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

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.svg2svg.SVGTranscoder;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 6/8/2015
 */
public final class Main {
	public static void main(String[] args) throws Throwable {
		args = new String[]{"--mapping", "mapping.txt", "--image", "image.txt"};
		OptionParser parser = new OptionParser();
		ArgumentAcceptingOptionSpec<Path> mappingOpt = parser.accepts("mapping").withRequiredArg().withValuesConvertedBy(new PathValueConverter()).required();
		ArgumentAcceptingOptionSpec<Path> imageOpt = parser.accepts("image").withRequiredArg().withValuesConvertedBy(new PathValueConverter()).required();
		OptionSet options = parser.parse(args);
		Path mappingPath = options.valueOf(mappingOpt);
		Path imagePath = options.valueOf(imageOpt);
		Mapping mapping = Mapping.fromFile(mappingPath);

		SVGDocument doc = (SVGDocument)SVGDOMImplementation.getDOMImplementation()
				.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, "svg", null);

		int tileHeight = 50, tileWidth = 50;
		List<String> imageLines = Files.readAllLines(imagePath);
		for (int r = 0; r < imageLines.size(); ++r) {
			String line = imageLines.get(r);
			for (int c = 0; c < line.length(); ++c) {
				char t = line.charAt(c);
				SVGSVGElement element = mapping.apply(t);
				if (element == null) {
					System.err.println("no mapping for symbol: "+t);
					continue;
				}

				String oldHeight = element.getAttribute("height");
				String oldWidth = element.getAttribute("width");

				element.setAttributeNS(null, "x", Integer.toString(c*tileWidth));
				element.setAttributeNS(null, "y", Integer.toString(r*tileHeight));
				element.setAttributeNS(null, "height", Integer.toString(tileHeight));
				element.setAttributeNS(null, "width", Integer.toString(tileWidth));
				element.setAttributeNS(null, "viewBox", String.format("0 0 %s %s", oldWidth, oldHeight));
				element.setAttributeNS(null, "preserveAspectRatio", "xMinYMin meet");
				doc.adoptNode(element);
				doc.getDocumentElement().appendChild(element);
			}
		}

		SVGTranscoder transcoder = new SVGTranscoder();
		try (Writer w = Files.newBufferedWriter(Paths.get("output.svg"))) {
			transcoder.transcode(new TranscoderInput(doc), new TranscoderOutput(w));
			w.flush();
		}
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
