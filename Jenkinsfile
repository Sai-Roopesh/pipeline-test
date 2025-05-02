pipeline {
    agent any

    /* ───── toolchains ───── */
    tools {
        jdk   'Java 21'
        maven 'Maven 3.8.1'
    }

    /* ───── global env ───── */
    environment {
        JAVA_HOME    = tool 'Java 21'
        M2_HOME      = tool 'Maven 3.8.1'
        SCANNER_HOME = tool 'sonar-scanner'
        PATH         = "${JAVA_HOME}/bin:${M2_HOME}/bin:${SCANNER_HOME}/bin:${env.PATH}"
      
    }

    triggers { githubPush() }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'main',
                    url:           'https://github.com/Sai-Roopesh/pipeline-test.git',
                    credentialsId: 'git-cred-test'
            }
        }

        stage('Build Application') {
            steps {
                sh 'mvn clean verify'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

       stage('Smoke Test') {
            steps {
                sh '''
                    set -eu
                    PORT=15000
                    JAR=target/my-app-1.0.1.jar

                    if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
                      echo "Port $PORT busy – killing old process"
                      fuser -k ${PORT}/tcp || true
                      sleep 1
                    fi

                    PORT=$PORT java -jar "$JAR" &
                    PID=$!
                    trap "kill $PID" EXIT

                    for i in {1..15}; do
                      if curl -sf "http://localhost:$PORT" >/dev/null; then break; fi
                      sleep 1
                    done

                    curl -sf "http://localhost:$PORT" | grep "Hello, Jenkins!"
                '''
            }
        }

        stage('SAST Scanning I') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('sonar') {
                        sh """
                            sonar-scanner \
                              -Dsonar.projectKey=pipeline-test \
                              -Dsonar.sources=src/main/java \
                              -Dsonar.tests=src/test/java \
                              -Dsonar.java.binaries=target/classes \
                              -Dsonar.login=\\$SONAR_TOKEN
                        """
                    }
                }
            }
        }

        stage('SAST Scanning II') {
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Push to Nexus') {
            steps {
                withMaven(globalMavenSettingsConfig: 'global-settings',
                          jdk: 'Java 21',
                          maven: 'Maven 3.8.1') {
                    sh 'mvn deploy -DskipTests'
                }
            }
        }

        stage('Build & Push Docker Container') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'docker-cred',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    script {
                        docker.withRegistry('', 'docker-cred') {
                            def img = docker.build("${DOCKER_USER}/boardgame:${BUILD_NUMBER}")
                            img.push()
              
                        }
                    }
                }
            }
        }

         
stage('Container Scanning I') {
  agent { label 'trivy' }
  steps {
    sh '''
      CACHE_DIR="$HOME/.trivy-cache"
      mkdir -p "$CACHE_DIR"
      # download only the database into the cache
      trivy image \
        --download-db-only \
        --cache-dir "$CACHE_DIR"
    '''
  }
}


stage('Container Scanning II') {
  agent { label 'trivy' }
  steps {
    withCredentials([usernamePassword(
      credentialsId: 'docker-cred',
      usernameVariable: 'DOCKER_USER',
      passwordVariable: 'IGNORED'
    )]) {
      sh '''
        CACHE_DIR="$HOME/.trivy-cache"
        trivy image \
          --cache-dir "$CACHE_DIR" \
          --timeout 30m \
          --exit-code 1 \
          --severity HIGH,CRITICAL \
          --format table \
          -o trivy.txt \
          ${DOCKER_USER}/boardgame:${BUILD_NUMBER}
      '''
    }
  }
  post {
    always {
      archiveArtifacts artifacts: 'trivy.txt', fingerprint: true
    }
  }
}


      
    
        stage('k8s Deployment I') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'docker-cred',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'IGNORED'
                )]) {
                    sh '''
                        export IMG_TAG="$DOCKER_USER/boardgame:${BUILD_NUMBER}"
                        envsubst < /var/lib/jenkins/k8s-manifest/deployment.yaml \
                                 > rendered-deployment.yaml
                    '''
                }
                // <<< Moved inside steps so the stage’s braces stay balanced:
                archiveArtifacts artifacts: 'rendered-deployment.yaml', fingerprint: true
            }
        }

        stage('k8s Deployment II') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh '''
                        kubectl apply -f rendered-deployment.yaml --record
                        kubectl rollout status deployment/nginx-deployment --timeout=1200s
                    '''
                }
            }
        }

        stage('k8s Deployment III') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh '''
                        echo "Verification Time: $(date +' %Y-%m-%d %H:%M:%S')"
                        kubectl get pods -l app=nginx \
                          -o custom-columns='NAME:.metadata.name,IMAGE:.spec.containers[*].image,READY:.status.containerStatuses[*].ready,START_TIME:.status.startTime' \
                          --no-headers
                        echo "Verification Time: $(date +' %Y-%m-%d %H:%M:%S')"
                    '''
                }
            }
        }

    } // end stages

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            script {
                def email = sh(
                    script: "git --no-pager show -s --format='%ae'",
                    returnStdout: true
                ).trim()
                mail to:      email,
                     subject: "✅ Deployment Successful: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                     body: """
Hello,

Your commit triggered a successful deployment for job '${env.JOB_NAME}' (build #${env.BUILD_NUMBER}).

See details: ${env.BUILD_URL}

Best,
Jenkins CI/CD
                     """
            }
        }
    }
}
