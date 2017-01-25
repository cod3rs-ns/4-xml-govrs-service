package rs.ac.uns.sw.xml.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xml.sax.SAXException;
import rs.ac.uns.sw.xml.domain.Amendments;
import rs.ac.uns.sw.xml.domain.Law;
import rs.ac.uns.sw.xml.service.AmendmentsServiceXML;
import rs.ac.uns.sw.xml.service.LawServiceXML;
import rs.ac.uns.sw.xml.service.ParliamentServiceXML;
import rs.ac.uns.sw.xml.states.StateContext;
import rs.ac.uns.sw.xml.util.*;
import rs.ac.uns.sw.xml.util.search_wrapper.SearchResult;
import rs.ac.uns.sw.xml.util.voting_wrapper.VotingObject;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;


@RestController
@RequestMapping("/api/laws")
public class LawRestController {

    private static final String NAME = "propis";

    @Autowired
    LawServiceXML service;

    @Autowired
    AmendmentsServiceXML amendmentsService;

    @Autowired
    ParliamentServiceXML parliamentServiceXML;

    @Autowired
    Transformers transformer;

    @Autowired
    StateContext stateContext;

    @RequestMapping(
            value = "/",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<Law> addLaw(@RequestBody Law law) throws URISyntaxException {
        if (stateContext.getState() == null) {
            return ResponseEntity
                    .badRequest()
                    .headers(HeaderUtil.failure(
                            Constants.EntityNames.PARLIAMENTS,
                            HeaderUtil.ERROR_CODE_NO_ACTIVE_PARLIAMENT,
                            HeaderUtil.ERROR_MSG_NO_ACTIVE_PARLIAMENT
                    ))
                    .body(null);
        }
        return (ResponseEntity<Law>) stateContext.getState().suggestLaw(law, stateContext.getParliament());
    }

    @RequestMapping(
            value = "/",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<SearchResult> getLaws() {
        final SearchResult result = service.getAll();

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @RequestMapping(
            value = "/{id}",
            method = RequestMethod.GET,
            produces = {
                    MediaType.APPLICATION_XML_VALUE,
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    MediaType.APPLICATION_XHTML_XML_VALUE
            }
    )
    public ResponseEntity<?> getLawById(@RequestHeader("Accept") String mediaType, @PathVariable String id)
            throws JAXBException, TransformerException, IOException, SAXException, ParserConfigurationException {

        final Law result = service.getOneById(id);

        HttpHeaders headers = new HttpHeaders();
        switch (mediaType) {
            case MediaType.APPLICATION_XML_VALUE: {
                headers.setContentType(MediaType.APPLICATION_XML);
                return new ResponseEntity<>(result, headers, HttpStatus.OK);
            }

            case MediaType.APPLICATION_OCTET_STREAM_VALUE: {
                transformer.setName(NAME);

                headers.set("Content-Disposition", "attachment; filename=law.pdf");
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                return new ResponseEntity<>(transformer.toPdf(RepositoryUtil.toXmlString(result, Law.class)), headers, HttpStatus.OK);
            }

            case MediaType.APPLICATION_XHTML_XML_VALUE: {
                transformer.setName(NAME);

                headers.setContentType(MediaType.APPLICATION_XHTML_XML);
                return new ResponseEntity<>(transformer.toHtml(RepositoryUtil.toXmlString(result, Law.class)), headers, HttpStatus.OK);
            }

            default:
                return new ResponseEntity<>(headers, HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/search",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<SearchResult> searchLaws(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "startVotesFor", required = false) Integer startVotesFor,
            @RequestParam(value = "endVotesFor", required = false) Integer endVotesFor,
            @RequestParam(value = "startVotesAgainst", required = false) Integer startVotesAgainst,
            @RequestParam(value = "endVotesAgainst", required = false) Integer endVotesAgainst,
            @RequestParam(value = "startVotesNeutral", required = false) Integer startVotesNeutral,
            @RequestParam(value = "endVotesNeutral", required = false) Integer endVotesNeutral,
            @RequestParam(value = "startDateOfProposal", required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") Date startDateOfProposal,
            @RequestParam(value = "endDateOfProposal", required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") Date endDateOfProposal,
            @RequestParam(value = "startDateOfVoting", required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") Date startDateOfVoting,
            @RequestParam(value = "endDateOfVoting", required = false) @DateTimeFormat(pattern = "dd-MM-yyyy") Date endDateOfVoting,
            @RequestParam(value = "status", required = false) String status
    ) throws URISyntaxException {

        MetaSearchWrapper wrapper = new MetaSearchWrapper()
                .startVotesFor(startVotesFor)
                .endVotesFor(endVotesFor)
                .startVotesAgainst(startVotesAgainst)
                .endVotesAgainst(endVotesAgainst)
                .startVotesNeutral(startVotesNeutral)
                .endVotesNeutral(endVotesNeutral)
                .startDateOfProposal(startDateOfProposal)
                .endDateOfProposal(endDateOfProposal)
                .startDateOfVoting(startDateOfVoting)
                .endDateOfVoting(endDateOfVoting)
                .status(status);

        final SearchResult result = service.getAllByQueryAndMetadata(query, wrapper);

        return ResponseEntity
                .ok()
                .body(result);
    }

    @RequestMapping(
            value = "/update/{amendmentsId}",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<Law> update(@PathVariable("amendmentsId") String amendmentsId) {

        Amendments amendments = amendmentsService.getOneById(amendmentsId);

        Law result = service.updateWithAmendments(amendments);

        return ResponseEntity
                .ok()
                .body(result);
    }

    @RequestMapping(
            value = "/{id}",
            method = RequestMethod.DELETE
    )
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        if (!service.lawExists(id)) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }

        service.deleteLawById(id);

        return ResponseEntity
                .ok()
                .build();
    }

    @RequestMapping(
            value = "/{id}/{status}",
            method = RequestMethod.PUT,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<Law> updateByStatus(@PathVariable("id") String id, @PathVariable("status") String status) {
        if (!service.lawExists(id)) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }

        return (ResponseEntity<Law>) stateContext.getState().updateLawStatus(id, status, stateContext.getParliament());
    }

    @RequestMapping(
            value = "/voting/{id}/",
            method = RequestMethod.PUT,
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<Law> updateVotes(@PathVariable("id") String id, @RequestBody VotingObject votes) {
        if (!service.lawExists(id)) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }

        return (ResponseEntity<Law>) stateContext.getState().updateLawVoting(id, votes);
    }


    @RequestMapping(value = "/users/{userId}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<SearchResult> getLawsByProposer(@PathVariable String userId) {
        final SearchResult result = service.searchByProposer(userId);

        return ResponseEntity
                .ok()
                .body(result);
    }

    @RequestMapping(
            value = "/metadata/json/{id}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public ResponseEntity<?> getMetadataJson(@PathVariable String id) {
        HttpHeaders headers = new HttpHeaders();

        String result = service.getMetadataJSON(id);

        headers.set("Content-Disposition", "attachment; filename=law.json");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        Object r = new InputStreamResource(new ByteArrayInputStream(result.getBytes()));

        return new ResponseEntity<>(r, headers, HttpStatus.OK);
    }

    @RequestMapping(
            value = "/metadata/xml/{id}",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public ResponseEntity<?> getMetadataXml(@PathVariable String id) {
        HttpHeaders headers = new HttpHeaders();

        String result = service.getMetadataXML(id);

        headers.set("Content-Disposition", "attachment; filename=" + id + ".xml");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        Object r = new InputStreamResource(new ByteArrayInputStream(result.getBytes()));

        return new ResponseEntity<>(r, headers, HttpStatus.OK);
    }
}
