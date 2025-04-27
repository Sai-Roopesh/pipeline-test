pipeline {
  agent any

  tools {
    jdk   'Java 21'
    maven 'Maven 3.8.1'
  }

  environment {
    JAVA_HOME       = tool 'Java 21'
    M2_HOME         = tool 'Maven 3.8.1'
    SCANNER_HOME    = tool 'sonar-scanner'
    PATH            = "${JAVA_HOME}/bin:${M2_HOME}/bin:${SCANNER_HOME}/bin:${env.PATH}"
    TRIVY_CACHE_DIR = "/var/lib/jenkins/trivy-cache"
    TRIVY_TEMPLATE  = "/usr/local/share/trivy/templates/html.tpl"
    DOCKER_CONFIG   = "${WORKSPACE}/.docker"
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
          archiveArtifacts artifacts: 'target/my-app-1.0.1.jar',
                            fingerprint: true
        }
      }
    }

    stage('Smoke Test') {
      steps {
        sh '''
          set -eu
          PORT=15000
          JAR=target/my-app-1.0.1.jar

          # Kill any process on the port
          if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null; then
            fuser -k ${PORT}/tcp || true
            sleep 1
          fi

          # Start the app and wait for it
          PORT=$PORT java -jar "$JAR" &
          PID=$!; trap "kill $PID" EXIT

          for i in {1..15}; do
            curl -sf "http://localhost:$PORT" && break
            sleep 1
          done

          # Final assertion
          curl -sf "http://localhost:$PORT" | grep "Hello, Jenkins!"
        '''
      }
    }

    stage('SonarQube Analysis') {
      when { expression { false } }
      steps {
        echo 'SonarQube analysis skipped.'
      }
    }

    stage('Quality Gate') {
      when { expression { false } }
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
      environment {
        DOCKER_CONFIG = "${WORKSPACE}/.docker"
      }
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
      post {
        always {
          sh 'rm -rf "$DOCKER_CONFIG"'
        }
      }
    }

    stage('Trivy Image Scan') {
      when { expression { false } }
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
            envsubst < /var/lib/jenkins/k8s-manifest/deployment.yaml \
                     > rendered-deployment.yaml
          '''
        }
        archiveArtifacts artifacts: 'rendered-deployment.yaml',
                         fingerprint: true
      }
    }

    stage('Deploy to k8s') {
      steps {
        withKubeConfig(credentialsId: 'k8s-config') {
          sh '''
            set -e
            kubectl apply -f rendered-deployment.yaml --record
            kubectl rollout status deployment/nginx-deployment \
              --timeout=120s
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
              -o custom-columns=NAME:.metadata.name,IMAGE:.spec.containers[*].image,READY:.status.containerStatuses[*].ready,CREATED:.metadata.creationTimestamp \
              --no-headers

            kubectl get svc nginx-service --ignore-not-found -o wide
          '''
        }
      }
    }
  } // end stages

  post {
    success {
      mail to:      'sairoopesh21@gmail.com',
           subject: "✅ ${env.JOB_NAME} #${env.BUILD_NUMBER} Succeeded",
           body:    """\
Build ${env.JOB_NAME} #${env.BUILD_NUMBER} finished ⇒ SUCCESS  
Details: ${env.BUILD_URL}
"""
    }
    failure {
      mail to:      'sairoopesh21@gmail.com',
           subject: "❌ ${env.JOB_NAME} #${env.BUILD_NUMBER} Failed",
           body:    "Please check the logs: ${env.BUILD_URL}"
    }
  }
}
