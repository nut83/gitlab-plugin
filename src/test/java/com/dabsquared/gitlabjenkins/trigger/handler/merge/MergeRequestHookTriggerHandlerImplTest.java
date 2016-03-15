package com.dabsquared.gitlabjenkins.trigger.handler.merge;

import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilter;
import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilterFactory;
import com.dabsquared.gitlabjenkins.trigger.filter.BranchFilterType;
import com.dabsquared.gitlabjenkins.trigger.handler.WebHookTriggerConfig;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.util.OneShotEvent;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static com.dabsquared.gitlabjenkins.model.builder.generated.CommitBuilder.commit;
import static com.dabsquared.gitlabjenkins.model.builder.generated.MergeRequestHookBuilder.mergeRequestHook;
import static com.dabsquared.gitlabjenkins.model.builder.generated.ObjectAttributesBuilder.objectAttributes;
import static com.dabsquared.gitlabjenkins.model.builder.generated.PushHookBuilder.pushHook;
import static com.dabsquared.gitlabjenkins.model.builder.generated.RepositoryBuilder.repository;
import static com.dabsquared.gitlabjenkins.trigger.filter.BranchFilterConfig.BranchFilterConfigBuilder.branchFilterConfig;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Robin Müller
 */
public class MergeRequestHookTriggerHandlerImplTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private MergeRequestHookTriggerHandler mergeRequestHookTriggerHandler;

    @Before
    public void setup() {
        mergeRequestHookTriggerHandler = new MergeRequestHookTriggerHandlerImpl();
    }

    @Test
    public void mergeRequest_ciSkip() throws IOException, InterruptedException {
        final OneShotEvent buildTriggered = new OneShotEvent();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                buildTriggered.signal();
                return true;
            }
        });
        mergeRequestHookTriggerHandler.handle(webHookTriggerConfig(true), project, mergeRequestHook()
                .withObjectAttributes(objectAttributes().withDescription("[ci-skip]").build())
                .build());

        buildTriggered.block(1000);
        assertThat(buildTriggered.isSignaled(), is(false));
    }

    @Test
    public void mergeRequest_build() throws IOException, InterruptedException, GitAPIException, ExecutionException {
        Git.init().setDirectory(tmp.getRoot()).call();
        tmp.newFile("test");
        Git git = Git.open(tmp.getRoot());
        git.add().addFilepattern("test");
        RevCommit commit = git.commit().setMessage("test").call();
        ObjectId head = git.getRepository().resolve(Constants.HEAD);
        String repositoryUrl = tmp.getRoot().toURI().toString();

        final OneShotEvent buildTriggered = new OneShotEvent();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.setScm(new GitSCM(repositoryUrl));
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                buildTriggered.signal();
                return true;
            }
        });
        mergeRequestHookTriggerHandler.handle(webHookTriggerConfig(true), project, mergeRequestHook()
                .withObjectAttributes(objectAttributes()
                        .withTargetBranch("refs/heads/" + git.nameRev().add(head).call().get(head))
                        .build())
                .build());

        buildTriggered.block();
        assertThat(buildTriggered.isSignaled(), is(true));
    }

    private WebHookTriggerConfig webHookTriggerConfig(final boolean ciSkip) {
        return new WebHookTriggerConfig() {
            @Override
            public boolean getCiSkip() {
                return ciSkip;
            }

            @Override
            public BranchFilter getBranchFilter() {
                return BranchFilterFactory.newBranchFilter(branchFilterConfig().build(BranchFilterType.All));
            }
        };
    }
}
