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
        TRIVY_CACHE_DIR = '/home/jenkins/.trivy-cache'
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

        stage('Build & Test') {
            steps {
                sh 'mvn clean verify'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

       /* stage('Smoke Test') {
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

        stage('SonarQube Analysis') {
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

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
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
        }*/

        stage('Build & Push Docker image') {
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

         

       stage('Prepare Trivy DB') {
      agent { label 'trivy' }
      steps {
        sh '''
          # make sure our cache directory exists
          mkdir -p $TRIVY_CACHE_DIR
          # download the full DB (vuln + secret + misconfig)
          trivy --cache-dir "$TRIVY_CACHE_DIR" db update
        '''
      }
    }

    stage('Trivy Image Scan') {
      agent { label 'trivy' }
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'docker-cred',
          usernameVariable: 'DOCKER_USER',
          passwordVariable: 'IGNORED'
        )]) {
          sh '''
            trivy image \
              --cache-dir $TRIVY_CACHE_DIR \
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

      
    
        stage('Render manifest') {
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

        stage('Deploy to k8s') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh '''
                        kubectl apply -f rendered-deployment.yaml --record
                        kubectl rollout status deployment/nginx-deployment --timeout=1200s
                    '''
                }
            }
        }

        stage('Verify deployment') {
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
