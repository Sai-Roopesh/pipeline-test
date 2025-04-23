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
  }

  stages {
    stage('Checkout') {
      steps {
        git branch:        'main',
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
          archiveArtifacts artifacts: 'target/my-app-1.0.1.jar', fingerprint: true
        }
      }
    }

    stage('Smoke Test') {
      steps {
        sh 'java -jar target/my-app-1.0.1.jar | grep "Hello, Jenkins!"'
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
                -Dsonar.java.binaries=target/classes \
                -Dsonar.login=${SONAR_TOKEN}
            """
          }
        }
      }
    }

    stage('Quality Gate') {
      steps {
        timeout(time: 2, unit: 'MINUTES') {
          waitForQualityGate abortPipeline: true
        }
      }
    }

    stage('Publish to Nexus') {
      steps {
        withMaven(globalMavenSettingsConfig: 'global-settings', jdk: 'Java 21', maven: 'Maven 3.8.1') {
          sh 'mvn deploy -DskipTests'
        }
      }
    }

    stage('Build & Tag Docker Image') {
      steps {
        script {
          withDockerRegistry(credentialsId: 'docker-cred', toolName: 'docker') {
            sh 'docker build -t thepraduman/boardgame:latest .'
          }
        }
      }
    }

    stage('Docker Image Scan') {
      steps {
        // long timeout so Trivy has time to pull its DB; only HIGH/CRITICAL to speed it up
        sh '''
          mkdir -p /var/lib/jenkins/.cache/trivy
          trivy image \
            --cache-dir /var/lib/jenkins/.cache/trivy \
            --scanners vuln \
            --severity HIGH,CRITICAL \
            --timeout 15m \
            --format table \
            -o trivy-image-report.html \
            thepraduman/boardgame:latest
        '''
      }
    }

    stage('Push Docker Image') {
      steps {
        script {
          withDockerRegistry(credentialsId: 'docker-cred', toolName: 'docker') {
            sh 'docker push thepraduman/boardgame:latest'
          }
        }
      }
    }

    stage('Deploy to k8s') {
      steps {
        withKubeConfig(
          credentialsId: 'k8s-cred',
          serverUrl:     'https://127.0.0.1:6443',
          namespace:     'webapps',
          clusterName:   'kubernetes'
        ) {
          sh 'kubectl apply -f k8s-manifest'
        }
      }
    }

    stage('Verify Deployment') {
      steps {
        withKubeConfig(
          credentialsId: 'k8s-cred',
          serverUrl:     'https://127.0.0.1:6443',
          namespace:     'webapps',
          clusterName:   'kubernetes'
        ) {
          sh 'kubectl get pods -n webapps'
          sh 'kubectl get svc -n webapps'
        }
      }
    }
  }

  post {
    always {
      junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
    }
  }
}
