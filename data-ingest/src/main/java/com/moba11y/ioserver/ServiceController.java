package com.moba11y.ioserver;

import com.google.gson.JsonElement;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
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

        private final byte[] familyBytes = Service.class.getSimpleName().getBytes();

        private static volatile long rowKey = 0;

        ServiceRepository() {
            super(Service.class);
        }

        @Override
        protected Put contsructPut(Service value) {
            Put put = new Put(DigestUtils.md5Hex("" + rowKey++).getBytes());

            put.addColumn(familyBytes, null, value.toJson().getBytes());

            return put;
        }

        @Override
        protected Service constructValue(Result result) {
            final String jsonString = new String(result.value());
            return (Service) GsonSerializable.fromJson(jsonString, Service.class);
        }
    }

    ServiceRepository repository = new ServiceRepository();

    @RequestMapping(method = RequestMethod.POST, value = "/service")
    public ResponseEntity saveService(@RequestBody Service value) throws IOException {
        repository.save(value);
        return ResponseEntity.ok().build();
    }

    /**
     * A method that allows you to fetch a simple HTML view of a subset of findings.
     * @param maxRecords Limit the number of results sent in the request data.
     * @return HTTP Response with findings from now since secondsBackInTime, containing
     *              now more than maxRecords Findings.
     */
    @RequestMapping(method = RequestMethod.GET, value = "/services")
    public ResponseEntity getUsers(@RequestParam(required = false, defaultValue = "100", value = "maxRecords") final int maxRecords) {
        try {

            List<Service> findings = repository.getValues();

            if (findings.size() > maxRecords) {
                findings = findings.subList(0, maxRecords);
            }

            return ResponseEntity.ok().body(findings);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(400).body(e);
        }
    }
}
