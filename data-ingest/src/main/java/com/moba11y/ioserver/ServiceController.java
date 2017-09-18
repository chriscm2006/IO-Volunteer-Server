package com.moba11y.ioserver;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Created by chrismcmeeking on 9/17/17.
 */

@RestController
public class ServiceController {

    @Repository
    private static class ServiceRepository extends HbaseRepository<Service> {

        private static volatile long rowKey = 0;
        ServiceRepository() {
            super(Service.class);
        }

        @Override
        protected byte[] generateRowKey(Service value) {
            return DigestUtils.md5Hex(value.getRequestor() + rowKey++).getBytes();
        }

        protected String getSchemaVersion() {
            return "1.0";
        }
    }

    ServiceRepository repository = new ServiceRepository();

    @RequestMapping(method = RequestMethod.POST, value = "/service")
    public ResponseEntity saveService(@RequestBody Service value) throws IOException {
        repository.save(value);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(method = RequestMethod.GET, value = "/service")
    public ResponseEntity getService(@RequestParam final String email) throws IOException {
        return ResponseEntity.ok().build();
    }

    /**
     * A method that allows you to fetch a simple HTML view of a subset of findings.
     * @param maxRecords Limit the number of results sent in the request data.
     * @return HTTP Response with findings from now since secondsBackInTime, containing
     *              now more than maxRecords Findings.
     */
    @RequestMapping(method = RequestMethod.GET, value = "/servicesDebug")
    public ResponseEntity getUsers(@RequestParam(required = false, defaultValue = "100", value = "maxRecords") final int maxRecords) {
        try {

            final long startTime = System.currentTimeMillis();

            List<Service> findings = repository.getFindings(new Service());

            if (findings.size() > maxRecords) {
                findings = findings.subList(0, maxRecords);
            }

            final long endTime = System.currentTimeMillis();

            final HashMap<String, Object> response = new HashMap<>();

            response.put("timeToScan", endTime - startTime);
            response.put("numFindingsInDatabase", findings.size());
            response.put("services", findings.toString());

            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(400).body(e);
        }
    }
}
