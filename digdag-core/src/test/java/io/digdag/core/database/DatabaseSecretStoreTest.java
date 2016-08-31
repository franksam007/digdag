package io.digdag.core.database;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import io.digdag.core.repository.Project;
import io.digdag.core.repository.ProjectStore;
import io.digdag.core.repository.StoredProject;
import io.digdag.spi.SecretControlStore;
import io.digdag.spi.SecretScopes;
import io.digdag.spi.SecretStore;
import org.junit.Before;
import org.junit.Test;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DatabaseSecretStoreTest
{
    private static final String SECRET = Base64.getEncoder().encodeToString(
            Strings.repeat(".", 16).getBytes(UTF_8));

    private static final int SITE_ID = 17;

    private static final String KEY1 = "foo1";
    private static final String KEY2 = "foo2";
    private static final String VALUE1 = "bar1";
    private static final String VALUE2 = "bar2";
    private static final String VALUE3 = "bar3";

    private DatabaseFactory factory;
    private StoredProject storedProject;
    private DatabaseSecretControlStoreManager controlStoreManager;
    private DatabaseSecretStoreManager storeManager;
    private SecretControlStore secretControlStore;
    private SecretStore secretStore;
    private int projectId;

    @Before
    public void setUp()
            throws Exception
    {
        factory = DatabaseTestingUtils.setupDatabase();
        ProjectStore projectStore = factory.getProjectStoreManager().getProjectStore(SITE_ID);
        Project project = Project.of("proj1");
        storedProject = projectStore.putAndLockProject(project, (store, stored) -> stored);

        controlStoreManager = factory.getSecretControlStoreManager(SECRET);
        storeManager = factory.getSecretStoreManager(SECRET);
        secretControlStore = controlStoreManager.getSecretControlStore(SITE_ID);
        secretStore = storeManager.getSecretStore(SITE_ID);
        projectId = storedProject.getId();
    }

    @Test
    public void noSecret()
            throws Exception
    {
        assertThat(secretControlStore.listProjectSecrets(projectId, SecretScopes.PROJECT), is(empty()));
        assertThat(secretStore.getSecret(projectId, KEY1, SecretScopes.PROJECT), is(Optional.absent()));
        secretControlStore.deleteProjectSecret(projectId, KEY1, SecretScopes.PROJECT);
    }

    @Test
    public void singleSecret()
            throws Exception
    {
        secretControlStore.setProjectSecret(projectId, SecretScopes.PROJECT, KEY1, VALUE1);
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT, KEY1), is(Optional.of(VALUE1)));
    }

    @Test
    public void multipleSecrets()
            throws Exception
    {
        secretControlStore.setProjectSecret(projectId, SecretScopes.PROJECT, KEY1, VALUE1);
        secretControlStore.setProjectSecret(projectId, SecretScopes.PROJECT, KEY2, VALUE2);
        secretControlStore.setProjectSecret(projectId, SecretScopes.PROJECT_DEFAULT, KEY2, VALUE3);

        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT, KEY1), is(Optional.of(VALUE1)));
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT, KEY2), is(Optional.of(VALUE2)));
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT_DEFAULT, KEY2), is(Optional.of(VALUE3)));

        // Delete with different scope should not delete the secret
        secretControlStore.deleteProjectSecret(projectId, SecretScopes.PROJECT_DEFAULT, KEY1);
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT, KEY1), is(Optional.of(VALUE1)));
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT, KEY2), is(Optional.of(VALUE2)));
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT_DEFAULT, KEY2), is(Optional.of(VALUE3)));

        secretControlStore.deleteProjectSecret(projectId, SecretScopes.PROJECT, KEY1);
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT, KEY1), is(Optional.absent()));
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT, KEY2), is(Optional.of(VALUE2)));
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT_DEFAULT, KEY2), is(Optional.of(VALUE3)));

        secretControlStore.deleteProjectSecret(projectId, SecretScopes.PROJECT, KEY2);
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT, KEY2), is(Optional.absent()));
        assertThat(secretStore.getSecret(projectId, SecretScopes.PROJECT_DEFAULT, KEY2), is(Optional.of(VALUE3)));
    }

    @Test
    public void getSecretWithScope()
            throws Exception
    {
        String[] scopes = {
                SecretScopes.PROJECT,
                SecretScopes.PROJECT_DEFAULT,
                "foobar"};

        for (String setScope : scopes) {
            secretControlStore.setProjectSecret(projectId, setScope, KEY1, VALUE1);
            assertThat(secretStore.getSecret(projectId, setScope, KEY1), is(Optional.of(VALUE1)));

            for (String getScope : scopes) {
                if (getScope.equals(setScope)) {
                    continue;
                }
                assertThat("set: " + setScope + ", get: " + getScope, secretStore.getSecret(projectId, getScope, KEY1), is(Optional.absent()));
            }

            secretControlStore.deleteProjectSecret(storedProject.getId(), setScope, KEY1);
        }
    }
}