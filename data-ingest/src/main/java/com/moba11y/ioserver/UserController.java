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

@RestController
public class UserController {

    @Repository
    private static class UserRepository extends HbaseRepository<User> {
        UserRepository() {
            super(User.class);
        }

        @Override
        protected byte[] generateRowKey(User value) {
            return DigestUtils.md5Hex(value.getEmail().getBytes()).getBytes();
        }

        protected String getSchemaVersion() {
            return "1.0";
        }
    }

    UserRepository repository = new UserRepository();

    @RequestMapping(method = RequestMethod.POST, value = "/user")
    public ResponseEntity saveUser(@RequestBody User value) throws IOException {
        repository.save(value);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(method = RequestMethod.GET, value = "/user")
    public ResponseEntity getUser(@RequestParam final String email) throws IOException {
        return ResponseEntity.ok().build();
    }

    /**
     * A method that allows you to fetch a simple HTML view of a subset of findings.
     * @param maxRecords Limit the number of results sent in the request data.
     * @return HTTP Response with findings from now since secondsBackInTime, containing
     *              now more than maxRecords Findings.
     */
    @RequestMapping(method = RequestMethod.GET, value = "/usersDebug")
    public ResponseEntity getUsers(@RequestParam(required = false, defaultValue = "100", value = "maxRecords") final int maxRecords) {
        try {

            final long startTime = System.currentTimeMillis();

            List<User> findings = repository.getFindings(new User());

            if (findings.size() > maxRecords) {
                findings = findings.subList(0, maxRecords);
            }

            final long endTime = System.currentTimeMillis();

            final HashMap<String, Object> response = new HashMap<>();

            response.put("timeToScan", endTime - startTime);
            response.put("numFindingsInDatabase", findings.size());
            response.put("findings", findings.toString());

            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(400).body(e);
        }
    }
}
