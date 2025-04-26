// Jenkinsfile – CI/CD pipeline (Sonar & Trivy still disabled)
pipeline {
    agent any

    tools {
        jdk   'Java 21'
        maven 'Maven 3.8.1'
    }

    environment {
        JAVA_HOME    = tool 'Java 21'
        M2_HOME      = tool 'Maven 3.8.1'
        SCANNER_HOME = tool 'sonar-scanner'           // harmless while skipped
        PATH         = "${JAVA_HOME}/bin:${M2_HOME}/bin:${SCANNER_HOME}/bin:${env.PATH}"

        TRIVY_CACHE_DIR = "/var/lib/jenkins/trivy-cache"
        TRIVY_TEMPLATE  = "/usr/local/share/trivy/templates/html.tpl"
        DOCKER_CONFIG   = "${WORKSPACE}/.docker"
    }

    triggers { githubPush() }

    stages {
        /* ───────── checkout & build ───────── */
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/Sai-Roopesh/pipeline-test.git',
                    credentialsId: 'git-cred-test'
            }
        }

        stage('Build & Test') {
            steps { sh 'mvn clean verify' }
            post { success { archiveArtifacts artifacts: 'target/*.jar', fingerprint: true } }
        }

        /* ───────── bullet-proof Smoke Test ───────── */
        stage('Smoke Test') {
            steps {
                sh '''
                  set -eu
                  PORT=8080
                  JAR=target/app.jar

                  # kill any stray process from an older build
                  if lsof -Pi :"$PORT" -sTCP:LISTEN -t >/dev/null 2>&1 ; then
                      echo "Port $PORT is busy – killing old process"
                      fuser -k ${PORT}/tcp || true
                      sleep 1
                  fi

                  # launch the server in background
                  java -jar "$JAR" &
                  PID=$!
                  trap "kill $PID" EXIT

                  # wait (max 15 s) until it answers
                  for i in {1..15}; do
                      if curl -sf http://localhost:${PORT} >/dev/null ; then break; fi
                      sleep 1
                  done

                  # assertion
                  curl -sf http://localhost:${PORT} | grep "Hello, Jenkins!"
                '''
            }
        }

        /* ───────── Sonar & Quality Gate (skipped) ───────── */
        stage('SonarQube Analysis') { when { expression { false } }  steps { echo 'Sonar skipped.' } }
        stage('Quality Gate')       { when { expression { false } }  steps { echo 'Gate skipped.' } }

        /* ───────── publish to Nexus ───────── */
        stage('Publish to Nexus') {
            steps {
                withMaven(globalMavenSettingsConfig: 'global-settings',
                          jdk: 'Java 21',
                          maven: 'Maven 3.8.1') {
                    sh 'mvn deploy -DskipTests'
                }
            }
        }

        /* ───────── Docker build & push ───────── */
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

        /* ───────── Trivy scan (still disabled) ───────── */
        stage('Trivy Image Scan') { when { expression { false } } steps { echo 'Trivy skipped.' } }

        /* ───────── render → deploy → verify ───────── */
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
                      echo "Pods / Image / Ready"
                      kubectl get pods -l app=nginx \
                        -o custom-columns='NAME:.metadata.name,IMAGE:.spec.containers[*].image,READY:.status.containerStatuses[*].ready' \
                        --no-headers
                      kubectl get svc nginx-service -o wide || true
                    '''
                }
            }
        }
    }

    post {
        always  { junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true }
        success {
            script {
                def email = sh(script: "git --no-pager show -s --format='%ae'", returnStdout: true).trim()
                mail to: email,
                     subject: "✅ Deployment Successful: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                     body: "Your commit deployed successfully. Details: ${env.BUILD_URL}"
            }
        }
    }
}
