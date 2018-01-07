package org.semanticweb.owlapi.io;

import static org.apache.commons.io.ByteOrderMark.UTF_16BE;
import static org.apache.commons.io.ByteOrderMark.UTF_16LE;
import static org.apache.commons.io.ByteOrderMark.UTF_32BE;
import static org.apache.commons.io.ByteOrderMark.UTF_32LE;
import static org.apache.commons.io.ByteOrderMark.UTF_8;
import static org.semanticweb.owlapi.io.ZipSources.handleZips;
import static org.semanticweb.owlapi.utilities.OWLAPIPreconditions.checkNotNull;
import static org.semanticweb.owlapi.utilities.OWLAPIPreconditions.verifyNotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.annotation.Nullable;

import org.apache.commons.io.input.BOMInputStream;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OntologyConfigurator;
import org.semanticweb.owlapi.utilities.PriorityCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;

/**
 * Base class for OWLOntologyDocumentSource.
 *
 * @author ignazio
 * @since 4.0.0
 */
public abstract class OWLOntologyDocumentSourceBase implements OWLOntologyDocumentSource {

    protected static final Logger LOGGER =
        LoggerFactory.getLogger(OWLOntologyDocumentSourceBase.class);
    private static final Pattern CONTENT_DISPOSITION_FILE =
        Pattern.compile(".*filename=\"([^\\s;]*)\".*");
    private static final String TEXTPLAIN_REQUEST_TYPE = ", text/plain; q=0.1";
    private static final String LAST_REQUEST_TYPE = ", */*; q=0.09";
    private static final String DEFAULT_REQUEST =
        "application/rdf+xml, application/xml; q=0.7, text/xml; q=0.6" + TEXTPLAIN_REQUEST_TYPE
            + LAST_REQUEST_TYPE;
    private static final LoadingCache<Integer, OkHttpClient> CACHE =
        Caffeine.newBuilder().maximumSize(16)
            .build((timeout) -> new OkHttpClient.Builder()
                .connectTimeout(timeout.longValue(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.longValue(), TimeUnit.MILLISECONDS).followRedirects(true)
                .followSslRedirects(true).build());
    protected final AtomicBoolean failedOnStreams = new AtomicBoolean(false);
    protected final AtomicBoolean failedOnIRI = new AtomicBoolean(false);
    private final IRI documentIRI;
    @Nullable
    private final OWLDocumentFormat format;
    @Nullable
    private final String mimeType;
    protected Charset encoding = StandardCharsets.UTF_8;
    private final StreamerWrapper<Reader, InputStream> defaultReader =
        (i) -> new InputStreamReader(new BOMInputStream(new BufferedInputStream(i), UTF_8, UTF_16BE,
            UTF_16LE, UTF_32BE, UTF_32LE), encoding);
    private Streamer<InputStream> inputStream;
    private Streamer<Reader> reader = () -> defaultReader.get(inputStream.get());
    protected String stringContent = "";
    @Nullable
    protected OWLParserParameters parametersAtLoading;
    @Nullable
    private String acceptHeaders = null;

    /**
     * Constructs an ontology input source using the specified file.
     *
     * @param iri document IRI
     * @param in input stream
     * @param format ontology format. If null, it is considered unspecified
     * @param mime mime type. If null or empty, it is considered unspecified.
     */
    protected OWLOntologyDocumentSourceBase(IRI iri, Streamer<InputStream> in,
        @Nullable OWLDocumentFormat format, @Nullable String mime) {
        this.format = format;
        mimeType = mime;
        documentIRI = checkNotNull(iri, "document iri cannot be null");
        inputStream = in;
    }

    /**
     * Constructs an ontology input source using the specified file.
     *
     * @param iriPrefix document IRI prefix - used to generate a new IRI
     * @param in input stream
     * @param format ontology format. If null, it is considered unspecified
     * @param mime mime type. If null or empty, it is considered unspecified.
     */
    protected OWLOntologyDocumentSourceBase(String iriPrefix, Streamer<InputStream> in,
        @Nullable OWLDocumentFormat format, @Nullable String mime) {
        this(IRI.getNextDocumentIRI(iriPrefix), in, format, mime);
    }

    @Override
    public Optional<String> getAcceptHeaders() {
        return Optional.ofNullable(acceptHeaders);
    }

    @Override
    public void setAcceptHeaders(String headers) {
        acceptHeaders = headers;
    }

    @Override
    @SuppressWarnings("null")
    public Optional<OWLOntologyLoaderMetaData> getOntologyLoaderMetaData() {
        return Optional.ofNullable(
            parametersAtLoading == null ? null : parametersAtLoading.getLoaderMetaData());
    }

    private static InputStream getInputStreamFromContentEncoding(IRI iri, Response response)
        throws IOException {
        String encoding = response.header("Content-Encoding");
        InputStream in = response.body().byteStream();
        if (encoding != null) {
            switch (encoding) {
                case "xz":
                    return new XZInputStream(in);
                case "gzip":
                    return new GZIPInputStream(in);
                case "deflate":
                    return new InflaterInputStream(in, new Inflater(true));
                default:
                    break;
            }
        }
        String fileName = getFileNameFromContentDisposition(response.header("Content-Disposition"));
        if (fileName == null) {
            fileName = iri.toString();
        }
        if (fileName.endsWith(".gz")) {
            return new GZIPInputStream(in);
        }
        if (fileName.endsWith(".xz")) {
            return new XZInputStream(in);
        }
        return handleZips(in, fileName);
    }

    private static Response getResponse(IRI documentIRI, OntologyConfigurator config,
        String acceptHeaders) throws IOException, OWLOntologyInputSourceException {
        String actualAcceptHeaders = acceptHeaders;
        if (!acceptHeaders.contains("text/plain")) {
            actualAcceptHeaders += TEXTPLAIN_REQUEST_TYPE;
        }
        if (!acceptHeaders.contains("*/*")) {
            actualAcceptHeaders += LAST_REQUEST_TYPE;
        }
        int count = 0;
        while (count < config.getRetriesToAttempt()) {
            try {
                count++;
                int timeout = count * config.getConnectionTimeout();
                return getResponse(documentIRI, timeout, actualAcceptHeaders,
                    config.getAuthorizationHeader());
            } catch (SocketTimeoutException e) {
                LOGGER.warn("Connection to " + documentIRI + " failed, attempt " + count + " of "
                    + config.getRetriesToAttempt(), e);
            }
        }
        throw new OWLOntologyInputSourceException(
            "cannot connect to " + documentIRI + "; retry limit exhausted");
    }

    /**
     * @param documentIRI iri to connect to
     * @param timeout connection timeout
     * @param acceptHeaders accept headers for the connection
     * @param authorizationHeader authorization header, if needed
     * @return Response for connection
     * @throws IOException if the connection fails
     */
    private static Response getResponse(IRI documentIRI, int timeout, String acceptHeaders,
        @Nullable String authorizationHeader) throws IOException {
        Builder builder = new Request.Builder().url(documentIRI.toString())
            .addHeader("Accept", acceptHeaders).addHeader("Accept-Encoding", "xz,gzip,deflate");
        if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
            builder.addHeader("Authorization", authorizationHeader);
        }
        Request request = builder.build();
        Call newCall = verifyNotNull(CACHE.get(Integer.valueOf(timeout))).newCall(request);
        return newCall.execute();
    }

    @Nullable
    private static String getFileNameFromContentDisposition(@Nullable String disposition) {
        if (disposition != null) {
            Matcher matcher = CONTENT_DISPOSITION_FILE.matcher(disposition);
            if (matcher.matches()) {
                return matcher.group(1).toLowerCase(Locale.getDefault());
            }
        }
        return null;
    }

    @Override
    public OWLDocumentFormat acceptParser(OWLParser parser, OWLOntology o,
        OntologyConfigurator config) {
        boolean textual = parser.getSupportedFormat().isTextual();
        OWLParserParameters parameters =
            new OWLParserParameters(o, config, documentIRI).withEncoding(encoding);
        parametersAtLoading = parameters;
        // For document sources that are string based, this is a performance
        // shortcut: no streams, no buffers, no IOExceptions
        if (!stringContent.isEmpty() && textual) {
            return parser.parse(stringContent, parameters);
        }
        if (!failedOnStreams.get()) {
            if (textual) {
                try (Reader r = reader.get()) {
                    return parser.parse(r, parameters);
                } catch (IOException e) {
                    LOGGER.error("Buffer cannot be opened", e);
                    failedOnStreams.set(true);
                    throw new OWLParserException(e);
                }
            }
            try (InputStream is = inputStream.get(); InputStream in = new BufferedInputStream(is)) {
                return parser.parse(in, parameters);
            } catch (IOException e) {
                failedOnStreams.set(true);
                throw new OWLParserException(e);
            }
        }
        if (!failedOnIRI.get()) {
            if (documentIRI.isFileIRI()) {
                try (InputStream is = new FileInputStream(new File(documentIRI.toURI()));
                    InputStream accountForZips = handleZips(is, documentIRI);
                    InputStream in = new BufferedInputStream(accountForZips)) {
                    if (textual) {
                        return parser.parse(defaultReader.get(in), parameters);
                    } else {
                        return parser.parse(in, parameters);
                    }
                } catch (IOException e) {
                    failedOnIRI.set(true);
                    throw new OWLParserException(e);
                }
            }
            if (documentIRI.getNamespace().startsWith("jar:")) {
                try (InputStream jarSource = streamFromJar().getInputStream();
                    InputStream in = new BufferedInputStream(jarSource)) {
                    if (textual) {
                        return parser.parse(defaultReader.get(in), parameters);
                    } else {
                        return parser.parse(in, parameters);
                    }
                } catch (IOException e) {
                    failedOnIRI.set(true);
                    throw new OWLParserException(e);
                }
            }
            try (
                Response response =
                    getResponse(documentIRI, config, getAcceptHeaders().orElse(DEFAULT_REQUEST));
                InputStream is = getInputStreamFromContentEncoding(documentIRI, response);
                InputStream in = new BufferedInputStream(is)) {
                if (textual) {
                    return parser.parse(defaultReader.get(in), parameters);
                } else {
                    return parser.parse(in, parameters);
                }
            } catch (OWLOntologyInputSourceException | IOException e) {
                failedOnIRI.set(true);
                throw new OWLParserException(e);
            }
        }
        throw new OWLParserException(
            "No input could be resolved - exceptions raised against Reader, InputStream and IRI resolution");
    }

    protected JarURLConnection streamFromJar() throws IOException, MalformedURLException {
        return (JarURLConnection) new URL(documentIRI.toString()).openConnection();
    }

    @Override
    public boolean loadingCanBeAttempted(Collection<String> parsableSchemes) {
        return !stringContent.isEmpty() || !failedOnStreams.get()
            || !failedOnIRI.get() && parsableSchemes.contains(documentIRI.getScheme());
    }

    @Override
    public final IRI getDocumentIRI() {
        return documentIRI;
    }

    @Override
    public PriorityCollection<OWLParserFactory> filter(
        PriorityCollection<OWLParserFactory> parsers) {
        if (parsers.isEmpty()) {
            return parsers;
        }
        if (format == null && mimeType == null) {
            return parsers;
        }
        PriorityCollection<OWLParserFactory> candidateParsers = parsers;
        if (format != null) {
            candidateParsers = parsers.getBySupportedFormat(format.getKey());
        }
        if (candidateParsers.isEmpty() && mimeType != null) {
            candidateParsers = parsers.getByMIMEType(mimeType);
        }
        if (candidateParsers.isEmpty()) {
            return parsers;
        }
        return candidateParsers;
    }

    interface Streamer<T> {

        T get() throws IOException;
    }

    interface StreamerWrapper<T, Q> {

        T get(Q q) throws IOException;
    }
}
