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
        PATH         = "${env.JAVA_HOME}/bin:${env.M2_HOME}/bin:${env.SCANNER_HOME}/bin:${env.PATH}"
    }

    triggers {
        githubPush()
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/Sai-Roopesh/pipeline-test.git',
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

                    java -jar "$JAR" --server.port=$PORT &
                    PID=$!
                    trap "kill $PID" EXIT

                    for i in {1..15}; do
                      curl -sf "http://localhost:$PORT" >/dev/null && break
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
                              -Dsonar.exclusions=**/generated/**,**/*.md,**/Dockerfile \
                              -Dsonar.login=\$SONAR_TOKEN
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
        }

        stage('Build & Push Docker Image') {
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
                            img.push('latest')
                        }
                    }
                }
            }
        }

        stage('Trivy Config-Only Scan') {
            steps {
                withCredentials([usernamePassword(
                  credentialsId: 'docker-cred',
                  usernameVariable: 'DOCKER_USER',
                  passwordVariable: 'IGNORED'
                )]) {
                    sh '''
                        trivy image \
                          --scanners config \
                          --format table \
                          -o trivy-config-report.html \
                          "$DOCKER_USER/boardgame:${BUILD_NUMBER}"
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-config-report.html', fingerprint: true
                    publishHTML(target: [
                        reportDir:   '.',
                        reportFiles: 'trivy-config-report.html',
                        reportName:  'Trivy Config Scan',
                        keepAll:     true,
                        alwaysLinkToLastBuild: true
                    ])
                }
            }
        }

        stage('Trivy Misconfig Scan') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                withCredentials([usernamePassword(
                  credentialsId: 'docker-cred',
                  usernameVariable: 'DOCKER_USER',
                  passwordVariable: 'IGNORED'
                )]) {
                    sh '''
                        trivy image \
                          --scanners misconfig \
                          --format table \
                          --timeout 5m \
                          --exit-code 0 \
                          -o trivy-misconfig-report.html \
                          "$DOCKER_USER/boardgame:${BUILD_NUMBER}"
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-misconfig-report.html', fingerprint: true
                    publishHTML(target: [
                        reportDir:   '.',
                        reportFiles: 'trivy-misconfig-report.html',
                        reportName:  'Trivy Misconfig Scan',
                        keepAll:     true,
                        alwaysLinkToLastBuild: true
                    ])
                }
            }
        }

        stage('Render k8s Manifest') {
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
                archiveArtifacts artifacts: 'rendered-deployment.yaml', fingerprint: true
            }
        }

        stage('Deploy to k8s') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh '''
                        kubectl apply -f rendered-deployment.yaml --record
                        kubectl rollout status deployment/nginx-deployment --timeout=120s
                        kubectl rollout status deployment/nginx-deployment --timeout=1200s
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh '''
                        echo "Pod ↔ Image ↔ Ready?"
                        kubectl get pods -l app=nginx \
                          -o custom-columns="NAME:.metadata.name,IMAGE:.spec.containers[*].image,READY:.status.containerStatuses[*].ready" \
                          --no-headers
                        kubectl get svc nginx-service -o wide || true
                    '''
                }
            }
        }
    }

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
                     body: """\
Hello,

Your commit triggered a successful deployment for job '${env.JOB_NAME}' (build #${env.BUILD_NUMBER}):

${env.BUILD_URL}

Best,
Jenkins CI/CD
"""
            }
        }
    }
}
