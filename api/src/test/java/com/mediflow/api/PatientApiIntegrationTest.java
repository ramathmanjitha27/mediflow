package com.mediflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full stack against the local Postgres: controller, service, JPA mapping and DB constraints.
 * Each test runs in a transaction that is rolled back, so no data is left behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PatientApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static String body(String givenName, String email) {
        return """
                {"givenName": "%s", "familyName": "Lovelace", "birthDate": "1990-12-10",
                 "gender": "female", "email": "%s"}
                """.formatted(givenName, email);
    }

    private long create(String givenName, String email) throws Exception {
        String json = mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body(givenName, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void crudRoundTrip() throws Exception {
        long id = create("Ada", "ada.it@example.com");

        mvc.perform(get("/api/patients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("female"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mvc.perform(put("/api/patients/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body("Augusta", "ada.it@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenName").value("Augusta"));

        mvc.perform(delete("/api/patients/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/patients/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        create("Ada", "dup.it@example.com");

        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body("Other", "DUP.IT@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void updateMayKeepOwnEmailButNotTakeAnother() throws Exception {
        create("Ada", "first.it@example.com");
        long second = create("Grace", "second.it@example.com");

        mvc.perform(put("/api/patients/{id}", second).contentType(MediaType.APPLICATION_JSON).content(body("Grace", "second.it@example.com")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/patients/{id}", second).contentType(MediaType.APPLICATION_JSON).content(body("Grace", "first.it@example.com")))
                .andExpect(status().isConflict());
    }
}
