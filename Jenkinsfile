pipeline {
    agent any

    tools {
        jdk   'Java 21'
        maven 'Maven 3.8.1'
    }

    environment {
        JAVA_HOME    = tool 'Java 21'
        M2_HOME      = tool 'Maven 3.8.1'
        SCANNER_HOME = tool 'sonar-scanner'
        PATH         = "${JAVA_HOME}/bin:${M2_HOME}/bin:${SCANNER_HOME}/bin:${env.PATH}"

        TRIVY_CACHE_DIR = "/var/lib/jenkins/trivy-cache"
        TRIVY_TEMPLATE  = "/usr/local/share/trivy/templates/html.tpl"
        DOCKER_CONFIG   = "${WORKSPACE}/.docker"
    }

    triggers { githubPush() }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/Sai-Roopesh/pipeline-test.git',
                    credentialsId: 'git-cred-test'
            }
        }

        stage('Build & Test') {
            steps { sh 'mvn clean verify' }
            post {
                success {
                    archiveArtifacts artifacts: 'target/my-app-1.0.1.jar', fingerprint: true
                }
            }
        }

        stage('Smoke Test') {
  steps {
    sh '''
      set -eu
      PORT=15000
      JAR=target/my-app-1.0.1.jar

      # 1) kill any stray process on PORT
      if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "Port $PORT busy – killing old process"
        fuser -k ${PORT}/tcp || true
        sleep 1
      fi

      # 2) launch the server with PORT in its env
      PORT=$PORT java -jar "$JAR" &
      PID=$!
      trap "kill $PID" EXIT

      # 3) wait (max 15s) until it answers
      for i in {1..15}; do
        if curl -sf "http://localhost:$PORT" >/dev/null; then
          break
        fi
        sleep 1
      done

      # 4) final assertion
      curl -sf "http://localhost:$PORT" | grep "Hello, Jenkins!"
    '''
  }
}


        stage('SonarQube Analysis') {
            when {
                expression { false }
            }
            steps {
                echo 'SonarQube analysis skipped.'
            }
        }

        stage('Quality Gate') {
            when {
                expression { false }
            }
            steps {
                echo 'Quality Gate skipped.'
            }
        }

        stage('Publish to Nexus') {
            steps {
                withMaven(globalMavenSettingsConfig: 'global-settings',
                          jdk: 'Java 21',
                          maven: 'Maven 3.8.1') {
                    sh 'mvn deploy -DskipTests'
                }
            }
        }

        stage('Build & Push Docker image') {
            environment { DOCKER_CONFIG = "${WORKSPACE}/.docker" }
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker-cred',
                                                  usernameVariable: 'DOCKER_USER',
                                                  passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                      set -e
                      mkdir -p "$DOCKER_CONFIG"
                      echo "$DOCKER_PASS" | docker login --username "$DOCKER_USER" --password-stdin

                      docker build -t "$DOCKER_USER/boardgame:${BUILD_NUMBER}" .
                      docker push "$DOCKER_USER/boardgame:${BUILD_NUMBER}"

                      docker tag "$DOCKER_USER/boardgame:${BUILD_NUMBER}" "$DOCKER_USER/boardgame:latest"
                      docker push "$DOCKER_USER/boardgame:latest"
                    '''
                }
            }
            post { always { sh 'rm -rf "$DOCKER_CONFIG"' } }
        }

        stage('Trivy Image Scan') {
            when {
                expression { false }
            }
            steps {
                echo 'Trivy scan skipped.'
            }
        }

        stage('Render manifest') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker-cred',
                                                  usernameVariable: 'DOCKER_USER',
                                                  passwordVariable: 'IGNORED')]) {
                    sh '''
                      export IMG_TAG="$DOCKER_USER/boardgame:${BUILD_NUMBER}"
                      envsubst < /var/lib/jenkins/k8s-manifest/deployment.yaml > rendered-deployment.yaml
                    '''
                }
                archiveArtifacts artifacts: 'rendered-deployment.yaml', fingerprint: true
            }
        }

        stage('Deploy to k8s') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh '''
                      set -e
                      kubectl apply -f rendered-deployment.yaml --record
                      kubectl rollout status deployment/nginx-deployment --timeout=120s
                    '''
                }
            }
        }

        stage('Verify deployment') {
  steps {
    withKubeConfig(credentialsId: 'k8s-config') {
      sh '''
        echo "Pods / Image / Ready / Created"
        kubectl get pods -l app=nginx \
          -o custom-columns=\
NAME:.metadata.name,IMAGE:.spec.containers[*].image,READY:.status.containerStatuses[*].ready,CREATED:.metadata.creationTimestamp \
          --no-headers

        # suppress “not found” error
        kubectl get svc nginx-service --ignore-not-found -o wide
      '''
    }
  }
}

    }

    post {
        always  {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
        }

        success {
            withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
                script {
                    // Capture GitHub username from Git log
                    def githubEmail = sh(script: "git --no-pager show -s --format='%ae'", returnStdout: true).trim()
                    def githubUser = githubEmail.split('@')[0].replaceAll("\\.", "")

                    def repoOwner = 'Sai-Roopesh'      // <-- Update if needed
                    def repoName  = 'pipeline-test'     // <-- Update if needed

                    // Post a GitHub comment mentioning the user
                    sh """
                      curl -X POST \
                        -H "Authorization: token ${GITHUB_TOKEN}" \
                        -H "Accept: application/vnd.github.v3+json" \
                        https://api.github.com/repos/${repoOwner}/${repoName}/issues/1/comments \
                        -d '{"body": "@${githubUser} ✅ Your application has been deployed! View here: ${BUILD_URL}"}'
                    """
                }
            }
        }
    }
}
