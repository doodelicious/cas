package org.apereo.cas.support.saml.web.idp.profile.builders.response;

import com.google.common.base.Throwables;
import org.apereo.cas.support.saml.OpenSamlConfigBean;
import org.apereo.cas.support.saml.SamlException;
import org.apereo.cas.support.saml.SamlUtils;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.support.saml.services.idp.metadata.SamlRegisteredServiceServiceProviderMetadataFacade;
import org.apereo.cas.support.saml.web.idp.profile.builders.SamlProfileObjectBuilder;
import org.apereo.cas.support.saml.web.idp.profile.builders.enc.BaseSamlObjectSigner;
import org.apereo.cas.support.saml.web.idp.profile.builders.enc.SamlObjectEncrypter;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPSOAP11Encoder;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.ecp.Response;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.soap.messaging.context.SOAP11Context;
import org.opensaml.soap.soap11.Body;
import org.opensaml.soap.soap11.Envelope;
import org.opensaml.soap.soap11.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.velocity.VelocityEngineFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The {@link SamlProfileSamlSoap11ResponseBuilder} is responsible for
 * building the final SAML assertion for the relying party.
 *
 * @author Misagh Moayyed
 * @since 4.2
 */
public class SamlProfileSamlSoap11ResponseBuilder extends BaseSamlProfileSamlResponseBuilder<Envelope> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SamlProfileSamlSoap11ResponseBuilder.class);
    private static final long serialVersionUID = -1875903354216171261L;

    private final SamlProfileObjectBuilder<? extends SAMLObject> saml2ResponseBuilder;

    public SamlProfileSamlSoap11ResponseBuilder(
            final OpenSamlConfigBean openSamlConfigBean,
            final BaseSamlObjectSigner samlObjectSigner,
            final VelocityEngineFactory velocityEngineFactory,
            final SamlProfileObjectBuilder<Assertion> samlProfileSamlAssertionBuilder,
            final SamlProfileObjectBuilder<? extends SAMLObject> saml2ResponseBuilder,
            final SamlObjectEncrypter samlObjectEncrypter) {
        super(openSamlConfigBean, samlObjectSigner, velocityEngineFactory, samlProfileSamlAssertionBuilder, samlObjectEncrypter);
        this.saml2ResponseBuilder = saml2ResponseBuilder;
    }

    @Override
    protected Envelope buildResponse(final Assertion assertion,
                                     final org.jasig.cas.client.validation.Assertion casAssertion,
                                     final AuthnRequest authnRequest,
                                     final SamlRegisteredService service,
                                     final SamlRegisteredServiceServiceProviderMetadataFacade adaptor,
                                     final HttpServletRequest request,
                                     final HttpServletResponse response,
                                     final String binding) throws SamlException {

        final AssertionConsumerService acs = adaptor.getAssertionConsumerService(binding);
        if (acs == null) {
            LOGGER.warn("Could not locate the assertion consumer service url for binding [{}]", SAMLConstants.SAML2_PAOS_BINDING_URI);
            throw new IllegalArgumentException("Failed to locate the assertion consumer service url for ECP");
        }

        LOGGER.debug("Located assertion consumer service url [{}]", acs);
        final Response ecpResponse = newEcpResponse(acs.getLocation());
        final Header header = newSoapObject(Header.class);
        header.getUnknownXMLObjects().add(ecpResponse);
        final Body body = newSoapObject(Body.class);
        final org.opensaml.saml.saml2.core.Response saml2Response = 
                buildSaml2Response(casAssertion, authnRequest, service, adaptor, request, binding);
        body.getUnknownXMLObjects().add(saml2Response);
        final Envelope envelope = newSoapObject(Envelope.class);
        envelope.setHeader(header);
        envelope.setBody(body);
        SamlUtils.logSamlObject(this.configBean, envelope);
        return envelope;
    }

    private org.opensaml.saml.saml2.core.Response buildSaml2Response(final org.jasig.cas.client.validation.Assertion casAssertion,
                                                                     final AuthnRequest authnRequest, final SamlRegisteredService service,
                                                                     final SamlRegisteredServiceServiceProviderMetadataFacade adaptor,
                                                                     final HttpServletRequest request,
                                                                     final String binding) {
        return (org.opensaml.saml.saml2.core.Response)
                saml2ResponseBuilder.build(authnRequest, request, null, 
                        casAssertion, service, adaptor, binding);
    }

    @Override
    protected Envelope encode(final SamlRegisteredService service,
                              final Envelope envelope,
                              final HttpServletResponse httpResponse,
                              final SamlRegisteredServiceServiceProviderMetadataFacade adaptor,
                              final String relayState,
                              final String binding) throws SamlException {
        try {
            final MessageContext result = new MessageContext();
            final SOAP11Context ctx = result.getSubcontext(SOAP11Context.class, true);
            ctx.setEnvelope(envelope);
            final HTTPSOAP11Encoder encoder = new HTTPSOAP11Encoder();
            encoder.setHttpServletResponse(httpResponse);
            encoder.setMessageContext(result);
            encoder.initialize();
            encoder.encode();
        } catch (final Exception e) {
            throw Throwables.propagate(e);
        }
        return envelope;
    }
}
