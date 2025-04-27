// Jenkinsfile – CI/CD pipeline with SonarQube, Trivy, Docker & Kubernetes
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

    TRIVY_CACHE_DIR = "/var/lib/jenkins/trivy-cache"
    TRIVY_TEMPLATE  = "/usr/local/share/trivy/templates/html.tpl"

    DOCKER_CONFIG   = "${WORKSPACE}/.docker"
  }

  triggers { githubPush() }

  stages {

    stage('Checkout') {
      steps {
        git branch: 'main',
            url:    'https://github.com/Sai-Roopesh/pipeline-test.git',
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

          # kill stray process
          if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo "Port $PORT busy – killing old process"
            fuser -k ${PORT}/tcp || true
            sleep 1
          fi

          # run in background
          PORT=$PORT java -jar "$JAR" &
          PID=$!
          trap "kill $PID" EXIT

          # wait up to 15s to become healthy
          for i in {1..15}; do
            if curl -sf "http://localhost:$PORT" >/dev/null; then break; fi
            sleep 1
          done

          # assert
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

            docker tag "$DOCKER_USER/boardgame:${BUILD_NUMBER}" \
                       "$DOCKER_USER/boardgame:latest"
            docker push "$DOCKER_USER/boardgame:latest"
          '''
        }
      }
      post { always { sh 'rm -rf "$DOCKER_CONFIG"' } }
    }

    stage('Trivy Image Scan') {
      steps {
        withCredentials([usernamePassword(
            credentialsId: 'docker-cred',
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'IGNORED'
        )]) {
          sh '''
            # ensure HTML template
            if [ ! -f "${TRIVY_TEMPLATE}" ]; then
              sudo mkdir -p "$(dirname "${TRIVY_TEMPLATE}")"
              sudo curl -sSL \
                https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl \
                -o "${TRIVY_TEMPLATE}"
            fi

            # warm the DB
            mkdir -p "${TRIVY_CACHE_DIR}"
            trivy image --download-db-only \
              --cache-dir "${TRIVY_CACHE_DIR}" --quiet

            # **only** run config & secret scans (no vuln):
            trivy image \
              --cache-dir "${TRIVY_CACHE_DIR}" \
              --scanners config,secret \
              --format template \
              --template "@${TRIVY_TEMPLATE}" \
              --timeout 15m \
              -o trivy-image-report.html \
              "$DOCKER_USER/boardgame:${BUILD_NUMBER}"
          '''
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'trivy-image-report.html', fingerprint: true
          publishHTML target: [
            reportDir: '.',
            reportFiles: 'trivy-image-report.html',
            reportName: 'Trivy Image Scan',
            keepAll: true,
            alwaysLinkToLastBuild: true
          ]
        }
      }
    }


    stage('Render manifest') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'docker-cred',
                                          usernameVariable: 'DOCKER_USER',
                                          passwordVariable: 'IGNORED')]) {
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
            # apply only your Deployment (no --prune)
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
            echo "Pod ↔ Image ↔ Ready?"
            kubectl get pods -l app=nginx \
              -o custom-columns='NAME:.metadata.name,IMAGE:.spec.containers[*].image,READY:.status.containerStatuses[*].ready' \
              --no-headers
            kubectl get svc nginx-service -o wide || true
          '''
        }
      }
    }
  }

  /* ───────── post-pipeline ───────── */
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

Your commit triggered a successful deployment for job '${env.JOB_NAME}'
(build #${env.BUILD_NUMBER}).

See details: ${env.BUILD_URL}

Best,
Jenkins CI/CD
"""
      }
    }
  }
}
