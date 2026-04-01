{{/*
Expand the name of the chart.
*/}}
{{- define "ala-collectory.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "ala-collectory.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "ala-collectory.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "ala-collectory.labels" -}}
helm.sh/chart: {{ include "ala-collectory.chart" . }}
{{ include "ala-collectory.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "ala-collectory.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ala-collectory.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Deployment annotations — includes a config checksum so that pods roll when
the config file changes.
*/}}
{{- define "ala-collectory.deploymentAnnotations" -}}
{{- $configChecksum := ( dict "checksum/collectory-config.properties" ( .Files.Get "config/collectory-config.properties" | sha256sum ) ) -}}
{{- $annotations := merge .Values.deploymentAnnotations $configChecksum -}}
{{ toYaml $annotations }}
{{- end }}
