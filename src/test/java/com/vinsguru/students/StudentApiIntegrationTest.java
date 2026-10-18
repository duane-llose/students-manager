package com.vinsguru.students;

import static org.assertj.core.api.Assertions.assertThat;

import com.vinsguru.students.dto.StudentRequest;
import com.vinsguru.students.dto.StudentResponse;
import com.vinsguru.students.repository.StudentRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StudentRepository repository;

    private RestClient client;

    @BeforeEach
    void setUp() {
        // The server handles requests on its own threads, so test-level @Transactional cannot roll back; clean explicitly.
        repository.deleteAll();
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/students")
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    private ResponseEntity<StudentResponse> create(StudentRequest request) {
        return client.post().body(request).retrieve().toEntity(StudentResponse.class);
    }

    @Test
    @DisplayName("Should create a student and return 201")
    void shouldCreateStudent() {
        var response = create(new StudentRequest("Alice", 15, "10A"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Alice");
        assertThat(response.getBody().age()).isEqualTo(15);
        assertThat(response.getBody().className()).isEqualTo("10A");
    }

    @Test
    @DisplayName("Should return all students")
    void shouldReturnAllStudents() {
        create(new StudentRequest("Alice", 15, "10A"));
        create(new StudentRequest("Bob", 16, "11B"));

        var response = client.get().retrieve().toEntity(new ParameterizedTypeReference<List<StudentResponse>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(StudentResponse::name).containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    @DisplayName("Should return a student by id")
    void shouldReturnStudentById() {
        var created = create(new StudentRequest("Alice", 15, "10A")).getBody();

        var response = client.get().uri("/{id}", created.id()).retrieve().toEntity(StudentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(created);
    }

    @Test
    @DisplayName("Should update an existing student")
    void shouldUpdateStudent() {
        var created = create(new StudentRequest("Alice", 15, "10A")).getBody();

        var response = client.put().uri("/{id}", created.id())
                .body(new StudentRequest("Alicia", 16, "11A"))
                .retrieve().toEntity(StudentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new StudentResponse(created.id(), "Alicia", 16, "11A"));
    }

    @Test
    @DisplayName("Should delete a student and return 204")
    void shouldDeleteStudent() {
        var created = create(new StudentRequest("Alice", 15, "10A")).getBody();

        var deleted = client.delete().uri("/{id}", created.id()).retrieve().toBodilessEntity();
        var fetched = client.get().uri("/{id}", created.id()).retrieve().toEntity(ProblemDetail.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 404 ProblemDetail when student does not exist")
    void shouldReturn404WhenNotFound() {
        var response = client.get().uri("/{id}", 9999).retrieve().toEntity(ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getDetail()).contains("9999");
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail when request is invalid")
    void shouldReturn400WhenInvalid() {
        var response = client.post().body(new StudentRequest(" ", 0, "")).retrieve().toEntity(ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).contains("name must not be blank", "age must be at least 1");
    }
}
