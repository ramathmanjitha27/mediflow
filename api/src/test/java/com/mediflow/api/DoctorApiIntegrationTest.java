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

/** Full stack against the local Postgres; each test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DoctorApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static final String BODY = """
            {"givenName": "Gregory", "familyName": "House", "specialty": "Diagnostic Medicine",
             "email": "house.it@example.com"}
            """;

    private long create(String body) throws Exception {
        String json = mvc.perform(post("/api/doctors").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void crudRoundTripAndActiveFlagHandling() throws Exception {
        long id = create(BODY);

        mvc.perform(get("/api/doctors/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        String deactivate = BODY.replace("}", ", \"active\": false}");
        mvc.perform(put("/api/doctors/{id}", id).contentType(MediaType.APPLICATION_JSON).content(deactivate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Omitting "active" on update leaves it unchanged
        mvc.perform(put("/api/doctors/{id}", id).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(delete("/api/doctors/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/doctors/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        create(BODY);

        mvc.perform(post("/api/doctors").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("house.it@", "HOUSE.IT@")))
                .andExpect(status().isConflict());
    }
}
