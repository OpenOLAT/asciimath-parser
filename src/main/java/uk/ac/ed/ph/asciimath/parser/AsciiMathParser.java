/* Copyright (c) 2011-2012, The University of Edinburgh.
 * All Rights Reserved.
 *
 * This file is part of AsciiMathParser.
 *
 * AsciiMathParser is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AsciiMathParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License (at http://www.gnu.org/licences/lgpl.html)
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AsciiMathParser. If not, see <http://www.gnu.org/licenses/lgpl.html>.
 */
package uk.ac.ed.ph.asciimath.parser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.w3c.dom.Document;

/**
 * Simple Java wrapper round AsciiMathParser.js that runs it using the Rhino JavaScript engine.
 * <p>
 * An instance of this class is thread-safe and reusable.
 *
 * @author David McKain
 */
public final class AsciiMathParser {

    /** Option to add <tt>display="block"</tt> to the resulting <tt>math</tt> element */
    public static final String OPTION_DISPLAY_MODE = "displayMode";

    /** Option to add an annotation to the resulting MathML showing the ASCIIMath input */
    public static final String OPTION_ADD_SOURCE_ANNOTATION = "addSourceAnnotation";

    /** Name of the AsciiMathParser.js file. */
    public static final String ASCIIMATH_PARSER_JS_NAME = "AsciiMathParser.js";

    /** Location of the bundled AsciiMathParser.js within the ClassPath. (It is included in the JAR file) */
    public static final String ASCIIMATH_PARSER_JS_LOCATION = "/uk/ac/ed/ph/asciimath/parser/AsciiMathParser.js";
    public static final String DOM_JS_LOCATION = "/uk/ac/ed/ph/asciimath/parser/dom.js";

    /** Shared scope used by a single instance of this Class */
    private final GenericObjectPool<AsciiMathEngine> sharedScope;

    public AsciiMathParser() {
        this(ASCIIMATH_PARSER_JS_LOCATION);
    }

    public AsciiMathParser(final String classPathLocation) {
        sharedScope = new GenericObjectPool<>(new AsciiMathEngineFactory());
        sharedScope.setTestOnBorrow(true);
        sharedScope.setTestOnReturn(true);
        sharedScope.setMaxTotal(10);
    }

    /**
     * Parses the given ASCIIMath input, returning a DOM {@link Document} Object containing the
     * resulting MathML <tt>math</tt> element.
     *
     * @see #parseAsciiMath(String, AsciiMathParserOptions)
     *
     * @param asciiMathInput ASCIIMath input, which must not be null
     * @return resulting MathML {@link Document} object
     *
     * @throws IllegalArgumentException if asciiMathInput is null
     * @throws AsciiMathParserException if an unexpected Exception happened
     */
    public String parseAsciiMath(final String asciiMathInput) {
        return parseAsciiMath(asciiMathInput, new AsciiMathParserOptions());
    }

    /**
     * Parses the given ASCIIMath input, returning a DOM {@link Document} Object containing the
     * resulting MathML <tt>math</tt> element, using the given {@link AsciiMathParserOptions} to
     * tweak the results.
     *
     * @see #parseAsciiMath(String)
     *
     * @param asciiMathInput ASCIIMath input, which must not be null
     * @param options optional {@link AsciiMathParserOptions}
     * @return resulting MathML {@link Document} object
     *
     * @throws IllegalArgumentException if asciiMathInput is null
     * @throws AsciiMathParserException if an unexpected Exception happened
     */
    public String parseAsciiMath(final String asciiMathInput, final AsciiMathParserOptions options) {
        if (asciiMathInput==null) {
            throw new IllegalArgumentException("AsciiMathInput must not be null");
        }

        /* Call up the JavaScript parsing code */
        AsciiMathEngine context = null;
        try {
            context = sharedScope.borrowObject();
            return context.transform(asciiMathInput, options);
        } catch (final Exception e) {
            throw new AsciiMathParserException("Error running AsciiMathParser.js on input", e);
        } finally {
            if(context != null) {
                try {
                    sharedScope.returnObject(context);
                } catch (final Exception e) {
                    throw new AsciiMathParserException("Error running AsciiMathParser.js on input", e);
                }
            }
        }
    }

    public static class AsciiMathEngine {

        private final ScriptEngine engine;

        public AsciiMathEngine(final ScriptEngine engine) {
            this.engine = engine;
        }

        public String transform(final String ascii, final AsciiMathParserOptions options) {
            try {
                final Boolean addSourceAnnotation = options == null ? Boolean.FALSE : new Boolean(options.isAddSourceAnnotation());
                final Boolean displayMode = options == null ? Boolean.FALSE : new Boolean(options.isDisplayMode());
                final Invocable invocable = (Invocable)engine;
                return (String)invocable.invokeFunction("parseAsciiMath", ascii, addSourceAnnotation, displayMode);
            }
            catch (final Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public static class AsciiMathEngineFactory implements PooledObjectFactory<AsciiMathEngine> {

        @Override
        public PooledObject<AsciiMathEngine> makeObject() throws Exception {
            final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            final InputStream domImplementation = AsciiMathParser.class.getResourceAsStream("dom.js");
            final Reader inDomReader = new InputStreamReader(domImplementation);
            engine.eval(inDomReader);
            final InputStream asciiMathScript = AsciiMathParser.class.getResourceAsStream("AsciiMathParser.js");
            final Reader inReader = new InputStreamReader(asciiMathScript);
            engine.eval(inReader);
            return new DefaultPooledObject<>(new AsciiMathEngine(engine));
        }

        @Override
        public void activateObject(final PooledObject<AsciiMathEngine> obj) throws Exception {
            //
        }

        @Override
        public void destroyObject(final PooledObject<AsciiMathEngine> arg0) throws Exception {
            //
        }

        @Override
        public void passivateObject(final PooledObject<AsciiMathEngine> arg0) throws Exception {
            //
        }

        @Override
        public boolean validateObject(final PooledObject<AsciiMathEngine> arg0) {
            return true;
        }
    }

    //--------------------------------------------------------------------------

    /**
     * Trivial main method that simply runs {@link AsciiMathParser} on the command line arguments
     * (concatenated together with spaces), printing out the resulting MathML document.
     */
    public static void main(final String[] args) {
        final StringBuilder inputBuilder = new StringBuilder();
        for (int i=0; i<args.length; i++) {
            inputBuilder.append(args[i]).append(" ");
        }
        final String asciiMathInput = inputBuilder.toString();

        final AsciiMathParser parser = new AsciiMathParser();
        final String result = parser.parseAsciiMath(asciiMathInput);

        System.out.println(result);
    }
}
