//comment-added
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

  triggers {
    githubPush()
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
        timeout(time: 5, unit: 'MINUTES') {
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
          docker.withRegistry('https://index.docker.io/v1/', 'docker-cred') {
            sh 'docker build -t sanika2003/boardgame:latest .'
          }
        }
      }
    }

    stage('Docker Image Scan') {
      steps {
        sh '''
          mkdir -p /var/lib/jenkins/.cache/trivy
          trivy image \
            --cache-dir /var/lib/jenkins/.cache/trivy \
            --scanners vuln \
            --severity HIGH,CRITICAL \
            --timeout 15m \
            --format table \
            -o trivy-image-report.html \
            sanika2003/boardgame:latest
        '''
      }
    }

    stage('Push Docker Image') {
      steps {
        script {
          docker.withRegistry('https://index.docker.io/v1/', 'docker-cred') {
            sh 'docker push sanika2003/boardgame:latest'
          }
        }
      }
    }

    stage('Deploy to k8s') {
      steps {
        withKubeConfig(
          credentialsId: 'k8s-config'
        ) {
          sh 'kubectl apply -f /var/lib/jenkins/k8s-manifest'
        }
      }
    }

    stage('Verify Deployment') {
      steps {
        withKubeConfig(
          credentialsId: 'k8s-config'
        ) {
          sh 'kubectl get pods -n ci-cd'
          sh 'kubectl get svc -n ci-cd'
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
        def authorEmail = sh(script: "git --no-pager show -s --format='%ae'", returnStdout: true).trim()
        mail to: authorEmail,
             subject: "✅ Deployment Successful: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
             body: """\
Hello,

Your commit triggered a successful deployment for job '${env.JOB_NAME}' (build #${env.BUILD_NUMBER}).

You can view it here: ${env.BUILD_URL}

Best,
Jenkins CI/CD
"""
      }
    }
  }
}
