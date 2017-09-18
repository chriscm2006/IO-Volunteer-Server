package com.moba11y.ioserver;

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

@RestController
public class UserController {

    @Repository
    private static class UserRepository extends HbaseRepository<User> {

        private final byte[] familyBytes = User.class.getSimpleName().getBytes();

        UserRepository() {
            super(User.class);
        }

        private static byte[] rowKey(final String username) {
            return DigestUtils.md5Hex(username.getBytes()).getBytes();
        }

        @Override
        protected Put contsructPut(User value) {
            Put put = new Put(rowKey(value.email));

            put.addColumn(familyBytes, null, value.toJson().getBytes());

            return put;
        }

        @Override
        protected User constructValue(Result result) {
            final String jsonString = new String(result.value());
            return (User)GsonSerializable.fromJson(jsonString, User.class);
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
        return ResponseEntity.ok(repository.get(UserRepository.rowKey(email)));
    }

    /**
     * A method that allows you to fetch a simple HTML view of a subset of findings.
     * @param maxRecords Limit the number of results sent in the request data.
     * @return HTTP Response with findings from now since secondsBackInTime, containing
     *              now more than maxRecords Findings.
     */
    @RequestMapping(method = RequestMethod.GET, value = "/users")
    public ResponseEntity getUsers(@RequestParam(required = false, defaultValue = "100", value = "maxRecords") final int maxRecords) {
        try {

            List<User> findings = repository.getValues();

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
