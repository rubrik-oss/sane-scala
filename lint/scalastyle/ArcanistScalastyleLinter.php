<?php

/**
 * Scalastyle linter for Arcanist.
 */
final class ArcanistScalastyleLinter extends ArcanistExternalLinter {
  private $configPath = null;
  private $jarPath = null;

  public function getDefaultBinary() {
    return 'java';
  }

  public function getInstallInstructions() {
    return pht('Download scalastyle-batch jar from '.
      'http://www.scalastyle.org/command-line.html and set the jarPath');
  }

  public function getInfoURI() {
    return 'http://www.scalastyle.org';
  }

  public function getInfoDescription() {
    return pht('Scalastyle linter');
  }

  public function getLinterConfigurationName() {
    return 'scalastyle';
  }

  protected function getMandatoryFlags() {
    return array(
      '-jar',
      $this->jarPath,
      '--config',
      $this->configPath,
    );
  }

  public function getLinterConfigurationOptions() {
    return parent::getLinterConfigurationOptions() + array(
      'scalastyle.config' => array(
        'type' => 'string',
        'help' => pht('Path to the scalastyle-configuration.xml'),
      ),
      'scalastyle.jar' => array(
        'type' => 'string',
        'help' => pht('Path to the scalastyle-batch jar file'),
      ),
    );
  }

  public function setLinterConfigurationValue($key, $value) {
    switch ($key) {
      case 'scalastyle.config':
        $this->configPath = Filesystem::resolvePath($value,
          $this->getProjectRoot());
        if (!file_exists($this->configPath)) {
          throw new ArcanistMissingLinterException(
            pht('Unable to locate scalastyle configuration at "%s"',
              $this->configPath));
        }
        return;
      case 'scalastyle.jar':
        $this->jarPath = Filesystem::resolvePath($value,
          $this->getProjectRoot());
        if (!file_exists($this->jarPath)) {
          throw new ArcanistMissingLinterException(
            pht('Unable to locate scalastyle jar at "%s". Try running ' .
                'sd_dev_bootstrap',
              $this->jarPath));
        }
        return;
    }

    parent::setLinterConfigurationValue($key, $value);
  }

  protected function parseLinterOutput($path, $err, $stdout, $stderr) {
    $output_pattern = '/^(?P<severity>warning|error|exception) '.
      'file=(?P<path>.*) message=(?P<name>.*) line=(?P<line>\d+)'.
      '(?: column=(?P<char>\d+))?$/';

    // When there's a syntax error, scalastyle's output format contains
    // neither the line number, nor the column number, but rather, the
    // index of the character in the entire file-content. The error line
    // is of the form:
    // error file=<path> message= ... ,<char_index>, ...
    $syntax_error_pattern = '/^(?P<severity>warning|error|exception) '.
      'file=(?P<path>.*) message=(?P<name>.*,(?P<char_in_file>\d+),.*)$/';

    $messages = array();
    $lines = explode(PHP_EOL, trim($stdout));

    foreach ($lines as $line) {
      $matches = null;
      if (preg_match($output_pattern, $line, $matches) ||
          preg_match($syntax_error_pattern, $line, $matches)) {
        $message = new ArcanistLintMessage();
        $message->setCode($this->getLinterName());
        $message->setSeverity(
          $this->mapScalastyleSeverity($matches['severity']));
        $message->setPath($matches['path']);
        $message->setName(str_replace('.message', '', $matches['name']));
        if (array_key_exists('line', $matches)) {
          $message->setLine($matches['line']);
        }
        if (array_key_exists('char', $matches)) {
          $message->setChar($matches['char']);
        }
        if (array_key_exists('char_in_file', $matches)) {
          $char_in_file = (int) $matches['char_in_file'];
          $file_contents = file_get_contents(
            $matches['path'], NULL, NULL, 0, $char_in_file
          );
          $newline_count = substr_count($file_contents, "\n");
          $message->setLine($newline_count + 1);
        }
        $messages[] = $message;
      }
    }

    return $messages;
  }

  public function getLinterName() {
    return 'scalastyle';
  }

  private function mapScalastyleSeverity($severity) {
    switch ($severity) {
      case 'warning':
        return ArcanistLintSeverity::SEVERITY_WARNING;
      case 'error':
        return ArcanistLintSeverity::SEVERITY_ERROR;
      case 'exception':
        return ArcanistLintSeverity::SEVERITY_DISABLED;
    }
    throw new Exception(
      pht('Unrecognized scalastyle severity "%s"', $severity),
      $this->getLinterName());
  }
}

